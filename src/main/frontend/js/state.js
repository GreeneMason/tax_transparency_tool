/**
 * GovLens State Management Module
 * 
 * Manages application state for UI rendering:
 * - Search results and pagination
 * - Loading and error states
 * - User interactions (selections, filters)
 * - Analytics events
 */

export class AppState {
  constructor() {
    this.searchQuery = '';
    this.searchResults = [];
    this.loadingState = 'idle'; // idle | loading | error | success
    this.errorMessage = null;
    this.currentPage = 0;
    this.pageSize = 25;
    this.totalResults = 0;
    this.stateFilter = null;
    this.selectedGovernments = new Map();
  }

  setLoading() {
    this.loadingState = 'loading';
    this.errorMessage = null;
  }

  setSuccess(results, pageSize, totalResults) {
    this.loadingState = 'success';
    this.searchResults = results;
    this.pageSize = pageSize;
    this.totalResults = totalResults;
    this.errorMessage = null;
  }

  setError(message) {
    this.loadingState = 'error';
    this.errorMessage = message;
    this.searchResults = [];
  }

  setQuery(query) {
    this.searchQuery = query;
    this.currentPage = 0;
  }

  setStateFilter(state) {
    this.stateFilter = state;
    this.currentPage = 0;
  }

  nextPage() {
    if (this.currentPage * this.pageSize + this.pageSize < this.totalResults) {
      this.currentPage++;
    }
  }

  prevPage() {
    if (this.currentPage > 0) {
      this.currentPage--;
    }
  }

  hasNextPage() {
    return (this.currentPage + 1) * this.pageSize < this.totalResults;
  }

  hasPrevPage() {
    return this.currentPage > 0;
  }

  getOffset() {
    return this.currentPage * this.pageSize;
  }
}

export class UIRenderer {
  constructor(state) {
    this.state = state;
  }

  renderSearchForm() {
    return `
      <form id="search-form" class="search-form">
        <div class="search-container">
          <input
            type="text"
            id="search-input"
            class="search-input"
            placeholder="Search government by name, city, or ZIP code"
            aria-label="Search governments"
            value="${escapeHtml(this.state.searchQuery)}"
          />
          <button type="submit" class="search-button" aria-label="Submit search">
            Search
          </button>
        </div>
        
        <div class="filter-group">
          <label for="state-filter" class="filter-label">Filter by state:</label>
          <select id="state-filter" class="state-filter" aria-label="Filter by state">
            <option value="">All States</option>
            <option value="WA" ${this.state.stateFilter === 'WA' ? 'selected' : ''}>Washington</option>
            <option value="CA">California</option>
            <option value="NY">New York</option>
            <!-- Add more states as needed -->
          </select>
        </div>
      </form>
    `;
  }

  renderLoadingState() {
    return `
      <div class="state-container state-loading" role="status" aria-live="polite">
        <div class="loader"></div>
        <p class="state-message">Searching governments...</p>
      </div>
    `;
  }

  renderErrorState() {
    return `
      <div class="state-container state-error" role="alert">
        <div class="error-icon">⚠</div>
        <p class="state-message">${escapeHtml(this.state.errorMessage)}</p>
        <button class="retry-button" id="retry-button">Try Again</button>
      </div>
    `;
  }

  renderEmptyState() {
    return `
      <div class="state-container state-empty">
        <div class="empty-icon">🔍</div>
        <p class="state-message">No governments found. Try adjusting your search.</p>
      </div>
    `;
  }

  renderResults() {
    if (!this.state.searchResults || this.state.searchResults.length === 0) {
      return this.renderEmptyState();
    }

    const results = this.state.searchResults.map(gov => `
      <div class="result-card" data-unit-id="${escapeHtml(gov.unitId)}" role="article">
        <div class="result-header">
          <h3 class="result-title">${escapeHtml(gov.unitName)}</h3>
          <span class="result-type" title="${escapeHtml(gov.govTypeDescription)}">
            ${escapeHtml(gov.govTypeCode)}
          </span>
        </div>
        <div class="result-details">
          <p class="detail-row">
            <span class="detail-label">Location:</span>
            <span class="detail-value">${escapeHtml(gov.county)} County, ${escapeHtml(gov.state)}</span>
          </p>
          ${gov.population ? `
            <p class="detail-row">
              <span class="detail-label">Population:</span>
              <span class="detail-value">${formatNumber(gov.population)}</span>
            </p>
          ` : ''}
        </div>
        <div class="result-actions">
          <button class="action-button action-details" data-unit-id="${escapeHtml(gov.unitId)}">
            View Details
          </button>
          <button class="action-button action-compare" data-unit-id="${escapeHtml(gov.unitId)}">
            Compare
          </button>
        </div>
      </div>
    `).join('');

    return `
      <div class="results-container">
        ${results}
      </div>
    `;
  }

  renderPagination() {
    if (this.state.totalResults === 0) return '';

    const startNum = this.state.getOffset() + 1;
    const endNum = Math.min(startNum + this.state.pageSize - 1, this.state.totalResults);

    return `
      <div class="pagination" role="navigation" aria-label="Search results pagination">
        <p class="pagination-info">
          Showing <strong>${startNum}</strong> to <strong>${endNum}</strong> of <strong>${this.state.totalResults}</strong> results
        </p>
        <div class="pagination-controls">
          <button 
            class="pagination-button" 
            id="prev-button"
            ${!this.state.hasPrevPage() ? 'disabled' : ''}
            aria-label="Previous page"
          >
            ← Previous
          </button>
          <span class="pagination-page">
            Page <strong>${this.state.currentPage + 1}</strong>
          </span>
          <button 
            class="pagination-button" 
            id="next-button"
            ${!this.state.hasNextPage() ? 'disabled' : ''}
            aria-label="Next page"
          >
            Next →
          </button>
        </div>
      </div>
    `;
  }

  renderFullPage() {
    return `
      <main id="main-content" class="main-content">
        <section class="search-section">
          ${this.renderSearchForm()}
        </section>

        <section class="results-section" aria-label="Search results">
          ${this.renderLoadingOrResults()}
          ${this.renderPagination()}
        </section>
      </main>
    `;
  }

  renderLoadingOrResults() {
    if (this.state.loadingState === 'loading') {
      return this.renderLoadingState();
    } else if (this.state.loadingState === 'error') {
      return this.renderErrorState();
    } else if (this.state.loadingState === 'success' && this.state.searchResults.length === 0) {
      return this.renderEmptyState();
    } else {
      return this.renderResults();
    }
  }
}

// Utility functions
function escapeHtml(text) {
  if (!text) return '';
  const element = document.createElement('div');
  element.textContent = text;
  return element.innerHTML;
}

function formatNumber(num) {
  return new Intl.NumberFormat('en-US').format(num);
}
