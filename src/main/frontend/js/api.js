/**
 * GovLens API Client Module
 * 
 * Handles all API communication with retry logic and error handling
 */

export class ApiClient {
  constructor(baseUrl = '/api/v1') {
    this.baseUrl = baseUrl;
    this.timeout = 30000; // 30 seconds
  }

  async searchGovernments(query, limit = 25, offset = 0, state = null) {
    const params = new URLSearchParams({
      query,
      limit,
      offset
    });

    if (state) {
      params.append('state', state);
    }

    return this.fetchWithRetry(`${this.baseUrl}/governments?${params}`);
  }

  async findGovernmentsByZip(zip, limit = 25, offset = 0, state = null) {
    const params = new URLSearchParams({
      zip,
      limit,
      offset
    });

    if (state) {
      params.append('state', state);
    }

    return this.fetchWithRetry(`${this.baseUrl}/governments/by-zip?${params}`);
  }

  async getExpenseBreakdown(unitId, year) {
    return this.fetchWithRetry(
      `${this.baseUrl}/governments/${encodeURIComponent(unitId)}/expense-breakdown?year=${year}`
    );
  }

  async compare(leftUnitId, rightUnitId, year) {
    const params = new URLSearchParams({
      leftUnitId,
      rightUnitId,
      year
    });

    return this.fetchWithRetry(`${this.baseUrl}/compare?${params}`);
  }

  async fetchWithRetry(url, maxRetries = 2, delayMs = 500) {
    let lastError;

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), this.timeout);

        const response = await fetch(url, {
          signal: controller.signal,
          headers: {
            'Accept': 'application/json'
          }
        });

        clearTimeout(timeoutId);

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        return await response.json();
      } catch (error) {
        lastError = error;

        if (attempt < maxRetries && this.isRetryable(error)) {
          // Exponential backoff
          const delay = delayMs * Math.pow(2, attempt);
          await new Promise(resolve => setTimeout(resolve, delay));
          continue;
        }

        throw error;
      }
    }

    throw lastError;
  }

  isRetryable(error) {
    // Retry on network errors and timeouts, not on client errors
    if (error.name === 'AbortError') return true;
    if (error instanceof TypeError && error.message.includes('Failed to fetch')) return true;
    return false;
  }
}
