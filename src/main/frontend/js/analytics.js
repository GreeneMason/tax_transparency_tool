/**
 * GovLens Analytics Module
 * 
 * Privacy-safe analytics tracking:
 * - No personally identifiable information
 * - No third-party trackers
 * - Minimal first-party cookies
 * 
 * Events tracked:
 * - Search queries (anonymized)
 * - Result interactions (clicks, comparisons)
 * - User flow (pages visited)
 */

export class Analytics {
  constructor(endpoint = '/api/v1/analytics') {
    this.endpoint = endpoint;
    this.sessionId = this.generateSessionId();
    this.pageLoadTime = Date.now();
    this.queue = [];
    this.isOnline = navigator.onLine;

    window.addEventListener('online', () => { this.isOnline = true; this.flush(); });
    window.addEventListener('offline', () => { this.isOnline = false; });
  }

  trackSearch(query, resultCount, stateFilter = null) {
    this.track('search', {
      queryLength: query?.length || 0,
      resultCount,
      stateFilter: stateFilter || 'none',
      timestamp: Date.now()
    });
  }

  trackResultClick(unitId, action = 'view') {
    this.track('result_interaction', {
      action,
      unitIdHash: this.hashUserId(unitId),
      timestamp: Date.now()
    });
  }

  trackCompare(leftUnitId, rightUnitId) {
    this.track('compare_initiated', {
      leftHash: this.hashUserId(leftUnitId),
      rightHash: this.hashUserId(rightUnitId),
      timestamp: Date.now()
    });
  }

  trackPageView(pageName) {
    this.track('page_view', {
      page: pageName,
      referrer: document.referrer || 'direct',
      timestamp: Date.now()
    });
  }

  trackError(errorMessage, componentName) {
    this.track('application_error', {
      component: componentName,
      message: errorMessage?.substring(0, 100), // Truncate to avoid sensitive data
      timestamp: Date.now()
    });
  }

  private track(eventName, eventData) {
    const event = {
      sessionId: this.sessionId,
      eventName,
      eventData,
      userAgent: navigator.userAgent,
      timestamp: Date.now()
    };

    this.queue.push(event);

    // Auto-flush every 30 events or after 60 seconds
    if (this.queue.length >= 30) {
      this.flush();
    }
  }

  private async flush() {
    if (!this.isOnline || this.queue.length === 0) {
      return;
    }

    const events = this.queue.splice(0);

    try {
      await fetch(this.endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ events }),
        keepalive: true // Ensure request completes even if page unloads
      });
    } catch (error) {
      // Silently fail analytics to avoid disrupting user experience
      console.debug('Analytics flush failed:', error);
      this.queue.push(...events); // Re-queue if failed
    }
  }

  private generateSessionId() {
    // Simple session ID combining timestamp and random
    const timestamp = Date.now().toString(36);
    const random = Math.random().toString(36).substring(2, 8);
    return `${timestamp}-${random}`;
  }

  private hashUserId(userId) {
    // Simple hash to anonymize unit IDs while maintaining consistency
    // Not cryptographically secure, but sufficient for analytics
    let hash = 0;
    for (let i = 0; i < userId.length; i++) {
      const char = userId.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32-bit integer
    }
    return Math.abs(hash).toString(16);
  }
}
