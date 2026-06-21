#!/usr/bin/env python3
"""
GovLens S3 Deploy — Phase 7.2

Uploads static assets and the SQLite map database to S3 with the headers
required for sql.js-httpvfs HTTP Range Request queries:

  - SQLite file is split into chunks (default 10 MB each) so the browser only
    caches the pages it actually reads rather than the full 100+ MB file.
  - A config.json manifest is generated alongside the chunks so sql.js-httpvfs
    can locate each segment via its `serverMode: "chunked"` config.
  - Static HTML assets get short Cache-Control TTLs (5 min default).
  - Content-Encoding is NEVER set on SQLite/chunk files — GZIP or Brotli
    compression breaks byte-range offsets and causes silent query failures.

Usage:
    python scripts/deploy_s3.py \\
        --bucket govlens-static \\
        --region us-east-1 \\
        [--prefix assets/]          # optional key prefix inside the bucket \\
        [--sqlite data/output/govlens_map.sqlite3] \\
        [--static-dir target/classes/static] \\
        [--chunk-size-mb 10]        # SQLite chunk size in MiB \\
        [--sqlite-key govlens_map]  # base key for chunks; config written to <key>.json \\
        [--html-ttl 300]            # Cache-Control max-age for HTML files (seconds) \\
        [--sqlite-ttl 86400]        # Cache-Control max-age for SQLite/chunk files \\
        [--apply-cors]              # PUT the CORS policy from infra/s3_cors.json \\
        [--dry-run]                 # print actions without uploading anything

Dependencies:
    pip install boto3

AWS credentials are read from the environment in the standard boto3 order:
  AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY env vars, ~/.aws/credentials, or
  the EC2 instance profile — whichever is present first.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

CHUNK_SUFFIX_LEN = 3          # chunk files are named e.g. chunk.000, chunk.001
REQUEST_CHUNK_SIZE = 4096     # SQLite page size; must match PRAGMA page_size in export script

STATIC_CONTENT_TYPES = {
    ".html": "text/html; charset=utf-8",
    ".css":  "text/css; charset=utf-8",
    ".js":   "application/javascript; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".png":  "image/png",
    ".svg":  "image/svg+xml",
    ".ico":  "image/x-icon",
    ".woff2": "font/woff2",
    ".woff":  "font/woff",
    ".ttf":   "font/ttf",
}


# ---------------------------------------------------------------------------
# Chunking helpers
# ---------------------------------------------------------------------------

def chunk_sqlite(sqlite_path: Path, chunk_size_bytes: int) -> list[bytes]:
    """Read the SQLite file and split it into equal-sized byte chunks."""
    data = sqlite_path.read_bytes()
    total = len(data)
    n_chunks = math.ceil(total / chunk_size_bytes)
    chunks = []
    for i in range(n_chunks):
        start = i * chunk_size_bytes
        end   = min(start + chunk_size_bytes, total)
        chunks.append(data[start:end])
    return chunks, total


def make_config(
    base_url: str,
    total_bytes: int,
    chunk_size_bytes: int,
    sqlite_key: str,
) -> dict:
    """
    Build the sql.js-httpvfs chunked config JSON.

    The browser will fetch:
      <urlPrefix>000, <urlPrefix>001, ...
    based on which 4 KB pages it needs for the current query.
    """
    return {
        "serverMode":          "chunked",
        "requestChunkSize":    REQUEST_CHUNK_SIZE,
        "databaseLengthBytes": total_bytes,
        "serverChunkSize":     chunk_size_bytes,
        "urlPrefix":           f"{base_url}/{sqlite_key}.chunk.",
        "suffixLength":        CHUNK_SUFFIX_LEN,
    }


# ---------------------------------------------------------------------------
# Upload helpers
# ---------------------------------------------------------------------------

def _put_object(
    s3,
    bucket: str,
    key: str,
    body: bytes,
    content_type: str,
    cache_control: str,
    dry_run: bool,
) -> None:
    size_kb = len(body) / 1024
    if dry_run:
        print(f"  [dry-run] PUT s3://{bucket}/{key}  ({size_kb:.0f} KB)  {content_type}  {cache_control}")
        return
    s3.put_object(
        Bucket=bucket,
        Key=key,
        Body=body,
        ContentType=content_type,
        CacheControl=cache_control,
        # ContentEncoding is intentionally omitted — never compress SQLite chunks
    )
    print(f"  ✓ s3://{bucket}/{key}  ({size_kb:.0f} KB)")


def upload_chunks(
    s3,
    bucket: str,
    prefix: str,
    sqlite_key: str,
    chunks: list[bytes],
    sqlite_ttl: int,
    dry_run: bool,
) -> None:
    print(f"\nUploading {len(chunks)} SQLite chunks...")
    for i, chunk in enumerate(chunks):
        suffix = str(i).zfill(CHUNK_SUFFIX_LEN)
        key    = f"{prefix}{sqlite_key}.chunk.{suffix}"
        _put_object(
            s3, bucket, key, chunk,
            content_type  = "application/octet-stream",
            cache_control = f"max-age={sqlite_ttl}, public",
            dry_run       = dry_run,
        )


def upload_config(
    s3,
    bucket: str,
    prefix: str,
    sqlite_key: str,
    config: dict,
    sqlite_ttl: int,
    dry_run: bool,
) -> None:
    key  = f"{prefix}{sqlite_key}.json"
    body = json.dumps(config, indent=2).encode()
    # Config is small; short TTL so clients pick up a new SQLite layout quickly.
    _put_object(
        s3, bucket, key, body,
        content_type  = "application/json; charset=utf-8",
        cache_control = f"max-age=300, public",
        dry_run       = dry_run,
    )


def upload_static_assets(
    s3,
    bucket: str,
    prefix: str,
    static_dir: Path,
    html_ttl: int,
    dry_run: bool,
) -> None:
    files = sorted(static_dir.rglob("*"))
    files = [f for f in files if f.is_file()]
    if not files:
        print(f"  Warning: no files found in {static_dir}")
        return

    print(f"\nUploading {len(files)} static asset(s) from {static_dir}...")
    for file in files:
        rel   = file.relative_to(static_dir)
        key   = f"{prefix}{rel.as_posix()}"
        ct    = STATIC_CONTENT_TYPES.get(file.suffix.lower(), "application/octet-stream")
        body  = file.read_bytes()
        _put_object(
            s3, bucket, key, body,
            content_type  = ct,
            cache_control = f"max-age={html_ttl}, public",
            dry_run       = dry_run,
        )


def apply_cors(s3, bucket: str, cors_policy_path: Path, dry_run: bool) -> None:
    with cors_policy_path.open() as f:
        policy = json.load(f)

    if dry_run:
        print(f"\n[dry-run] PUT bucket CORS from {cors_policy_path}")
        print(json.dumps(policy, indent=2))
        return

    s3.put_bucket_cors(
        Bucket=bucket,
        CORSConfiguration=policy,
    )
    print(f"\n✓ CORS policy applied to bucket '{bucket}'")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Deploy GovLens static assets and SQLite map database to S3."
    )
    parser.add_argument("--bucket",         required=True,  help="S3 bucket name")
    parser.add_argument("--region",         default="us-east-1")
    parser.add_argument("--prefix",         default="",     help="Key prefix inside the bucket (e.g. 'assets/')")
    parser.add_argument(
        "--sqlite",
        default="data/output/govlens_map.sqlite3",
        help="Path to SQLite file to upload (relative to workspace root or absolute)",
    )
    parser.add_argument(
        "--static-dir",
        default="target/classes/static",
        help="Directory of built static assets to upload",
    )
    parser.add_argument("--chunk-size-mb",  type=float, default=10.0, help="SQLite chunk size in MiB")
    parser.add_argument("--sqlite-key",     default="govlens_map",    help="Base S3 key for SQLite chunks and config")
    parser.add_argument("--html-ttl",       type=int,   default=300,   help="Cache-Control max-age for HTML/CSS/JS (seconds)")
    parser.add_argument("--sqlite-ttl",     type=int,   default=86400, help="Cache-Control max-age for SQLite chunks (seconds)")
    parser.add_argument("--apply-cors",     action="store_true",       help="Apply CORS policy from infra/s3_cors.json")
    parser.add_argument(
        "--cors-policy",
        default="infra/s3_cors.json",
        help="Path to CORS policy JSON (used with --apply-cors)",
    )
    parser.add_argument("--skip-sqlite",    action="store_true", help="Skip SQLite upload")
    parser.add_argument("--skip-static",    action="store_true", help="Skip static asset upload")
    parser.add_argument("--dry-run",        action="store_true", help="Print actions without uploading")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    # Resolve paths relative to workspace root (the directory containing this script's parent)
    workspace = Path(__file__).resolve().parent.parent

    sqlite_path    = Path(args.sqlite)    if Path(args.sqlite).is_absolute()     else workspace / args.sqlite
    static_dir     = Path(args.static_dir) if Path(args.static_dir).is_absolute() else workspace / args.static_dir
    cors_path      = Path(args.cors_policy) if Path(args.cors_policy).is_absolute() else workspace / args.cors_policy

    prefix = args.prefix
    if prefix and not prefix.endswith("/"):
        prefix += "/"

    # Boto3 import (deferred so the rest of the script can be imported safely)
    try:
        import boto3
        from botocore.exceptions import BotoCoreError, ClientError
    except ImportError:
        print("Error: boto3 is not installed. Run: pip install boto3", file=sys.stderr)
        return 1

    s3 = boto3.client("s3", region_name=args.region)

    # ── CORS ──────────────────────────────────────────────────────────────────
    if args.apply_cors:
        if not cors_path.exists():
            print(f"Error: CORS policy file not found: {cors_path}", file=sys.stderr)
            return 1
        apply_cors(s3, args.bucket, cors_path, args.dry_run)

    # ── Static assets ─────────────────────────────────────────────────────────
    if not args.skip_static:
        if not static_dir.exists():
            print(f"Error: static dir not found: {static_dir}", file=sys.stderr)
            print("Run 'mvn package' first to build the production assets.", file=sys.stderr)
            return 1
        upload_static_assets(s3, args.bucket, prefix, static_dir, args.html_ttl, args.dry_run)

    # ── SQLite chunks ─────────────────────────────────────────────────────────
    if not args.skip_sqlite:
        if not sqlite_path.exists():
            print(f"Error: SQLite file not found: {sqlite_path}", file=sys.stderr)
            print("Run 'python scripts/export_sqlite_map.py' first.", file=sys.stderr)
            return 1

        chunk_size = int(args.chunk_size_mb * 1024 * 1024)
        size_mb    = sqlite_path.stat().st_size / 1_048_576

        print(f"\nSplitting {sqlite_path.name} ({size_mb:.1f} MB) into {args.chunk_size_mb} MB chunks...")
        chunks, total_bytes = chunk_sqlite(sqlite_path, chunk_size)
        print(f"  {len(chunks)} chunk(s)  |  {total_bytes:,} bytes total")

        # Build the public URL prefix for the config manifest.
        # S3 virtual-hosted-style URL: https://<bucket>.s3.<region>.amazonaws.com
        bucket_url = f"https://{args.bucket}.s3.{args.region}.amazonaws.com"
        base_url   = f"{bucket_url}/{prefix.rstrip('/')}" if prefix else bucket_url

        config = make_config(base_url, total_bytes, chunk_size, args.sqlite_key)

        upload_chunks(s3, args.bucket, prefix, args.sqlite_key, chunks, args.sqlite_ttl, args.dry_run)
        upload_config(s3, args.bucket, prefix, args.sqlite_key, config, args.sqlite_ttl, args.dry_run)

        print(f"\nConfig URL: {base_url}/{args.sqlite_key}.json")
        print("Pass this URL as the 'config' option to sql.js-httpvfs.")

    print("\nDone.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
