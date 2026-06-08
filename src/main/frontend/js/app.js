/**
 * GovLens Main Application Entry Point
 * 
 * Initializes:
 * - State management
 * - Event handlers
 * - API client
 * - Analytics
 * - Accessibility features
 */

import { AppState, UIRenderer } from './state.js';
import { ApiClient } from './api.js';
import { Analytics } from './analytics.js';
import '../css/styles.css';

class GovLensApp {
  constructor() {
    this.state = new AppState();
    this.renderer = new UIRenderer(this.state);
    this.api = new ApiClient();
    this.analytics = new Analytics();

    this.init();
  }

  async init() {
    // Initialize page
    document.body.innerHTML = this.renderer.renderFullPage();

    // Attach event listeners
    this.attachEventListeners();

    // Track initial page view
    this.analytics.trackPageView('home');

    // Set focus to main content for accessibility
    const mainContent = document.getElementById('main-content');
    if (mainContent) {
      mainContent.tabIndex = -1;
      mainContent.focus();
    }

    // Initialize service worker (optional, for offline support)
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js').catch(err => {
        console.debug('Service worker registration failed:', err);
      });
    }
  }

  attachEventListeners() {
    // Search form submission
    const searchForm = document.getElementById('search-form');
    if (searchForm) {
      searchForm.addEventListener('submit', (e) => this.handleSearch(e));
    }

    // State filter change
    const stateFilter = document.getElementById('state-filter');
    if (stateFilter) {
      stateFilter.addEventListener('change', (e) => this.handleStateFilterChange(e));
    }

    // Search input for real-time feedback
    const searchInput = document.getElementById('search-input');
    if (searchInput) {
      searchInput.addEventListener('input', (e) => {
        this.state.setQuery(e.target.value);
      });
    }

    // Pagination
    document.addEventListener('click', (e) => {
      if (e.target.id === 'next-button') this.handleNextPage(e);
      if (e.target.id === 'prev-button') this.handlePrevPage(e);
      if (e.target.id === 'retry-button') this.handleRetry(e);
      if (e.target.classList.contains('action-details')) this.handleResultClick(e);
      if (e.target.classList.contains('action-compare')) this.handleCompareClick(e);
    });

    // Keyboard navigation
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && e.target.id === 'search-input') {
        searchForm?.dispatchEvent(new Event('submit'));
      }
    });
  }

  async handleSearch(event) {
    event.preventDefault();

    const query = document.getElementById('search-input')?.value?.trim() || '';

    if (query.length < 2) {
      this.state.setError('Search must be at least 2 characters.');
      this.render();
      return;
    }

    this.state.setQuery(query);
    await this.performSearch();
  }

  async performSearch() {
    this.state.setLoading();
    this.render();

    try {
      const response = await this.api.searchGovernments(
        this.state.searchQuery,
        this.state.pageSize,
        this.state.getOffset(),
        this.state.stateFilter
      );

      const results = response.data.map(item => ({
        unitId: item.unit_id,
        unitName: item.unit_name,
        county: item.county_name,
        state: item.state_abbrev,
        govTypeCode: item.gov_type_code,
        govTypeDescription: item.gov_type_description,
        population: item.population
      }));

      this.state.setSuccess(results, response.pagination.limit, response.pagination.total_count);
      this.analytics.trackSearch(this.state.searchQuery, results.length, this.state.stateFilter);
      this.render();
    } catch (error) {
      console.error('Search failed:', error);
      this.state.setError('Failed to search. Please try again.');
      this.analytics.trackError(error.message, 'SearchComponent');
      this.render();
    }
  }

  handleStateFilterChange(event) {
    this.state.setStateFilter(event.target.value || null);
    if (this.state.searchQuery) {
      this.performSearch();
    }
  }

  async handleNextPage(event) {
    event.preventDefault();
    if (this.state.hasNextPage()) {
      this.state.nextPage();
      await this.performSearch();
      window.scrollTo(0, 0);
    }
  }

  async handlePrevPage(event) {
    event.preventDefault();
    if (this.state.hasPrevPage()) {
      this.state.prevPage();
      await this.performSearch();
      window.scrollTo(0, 0);
    }
  }

  handleRetry(event) {
    event.preventDefault();
    this.performSearch();
  }

  handleResultClick(event) {
    event.preventDefault();
    const unitId = event.target.dataset.unitId;
    if (unitId) {
      this.analytics.trackResultClick(unitId, 'view');
      window.location.href = `/details.html?unitId=${encodeURIComponent(unitId)}`;
    }
  }

  handleCompareClick(event) {
    event.preventDefault();
    const unitId = event.target.dataset.unitId;
    if (unitId) {
      this.state.selectedGovernments.set(unitId, true);
      
      if (this.state.selectedGovernments.size >= 2) {
        const [left, right] = this.state.selectedGovernments.keys();
        this.analytics.trackCompare(left, right);
        window.location.href = `/compare.html?left=${encodeURIComponent(left)}&right=${encodeURIComponent(right)}`;
      } else {
        alert('Select another government to compare');
      }
    }
  }

  render() {
    const resultsSection = document.querySelector('.results-section');
    if (resultsSection) {
      resultsSection.innerHTML = 
        this.renderer.renderLoadingOrResults() + 
        this.renderer.renderPagination();
    }
  }
}

// Initialize app when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => new GovLensApp());
} else {
  new GovLensApp();
}
