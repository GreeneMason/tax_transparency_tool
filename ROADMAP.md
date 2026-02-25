# 🏛️ Project: GovLens (City Tax Comparison)

## Mission
To transform cryptic government "Public Use Files" into clear, actionable insights for the average citizen.

## 📍 Project Roadmap

### Scope Strategy
- **MVP Scope:** Washington State only.
- **Expansion Plan:** Roll out to the full USA after the Washington pipeline, API, and dashboard are stable.

### Phase 1: Data Ingestion & Engineering (The "Library" Layer)
**Focus:** Java, Data Structures, and Database Design.

- [ ] **Parser Development:** Build a Java-based Fixed-Width file reader to parse the Census `2023FinEstDAT` file for Washington records first.
- [ ] **ID Mapping:** Implement a HashMap lookup system using the `Fin_PID_2023` file to associate Washington Unit IDs with City/County names.
- [ ] **Normalization Logic:** Create a "Translation Engine" to map 3-digit Census Item Codes (e.g., `29U`, `062`) into human-readable categories.
- [ ] **Database Schema:** Design a PostgreSQL database to store yearly financial snapshots for indexed Washington cities.
- [ ] **Scale Prep:** Add state-parameterized ingestion logic so nationwide expansion can be enabled without schema redesign.

### Phase 2: API & Backend Services (The "Engine Room")
**Focus:** Spring Boot and RESTful API design.

- [ ] **Search API:** Create an endpoint that allows the frontend to search Washington cities by name or zip code.
- [ ] **Comparison Logic:** Develop an API service that takes two City IDs and returns a JSON payload of their normalized spending side-by-side.
- [ ] **Tax Calculator Service:** Build a logic layer that takes a user-inputted income/property value and calculates their "personal contribution" to each budget category.
- [ ] **National Rollout Endpoint Plan:** Define pagination/filtering conventions that will support all-state datasets later.

### Phase 3: Frontend & Data Viz (The "Gallery")
**Focus:** React, D3.js, and Aesthetic Design.

- [ ] **Aesthetic Setup:** Implement a "Dark Academia" theme using Tailwind CSS (think: `#1a1a1b` backgrounds, serif fonts like Playfair Display, and parchment-colored accents).
- [ ] **The "Money Flow" Chart:** Build a D3.js Sankey Diagram to show taxes flowing from revenue sources into departments.
- [ ] **Side-by-Side Dashboard:** Create a responsive comparison view that highlights the biggest spending differences between two Washington cities.

### Phase 4: Mission & Transparency (The "Town Square")
**Focus:** Impact and User Engagement.

- [ ] **Glossary of Terms:** Add an interactive "Legalese-to-English" dictionary for budget items.
- [ ] **Source Verification:** Include direct links back to the original Census Bureau Public Use Files for every data point to ensure 100% transparency.
- [ ] **Export to PDF:** Allow users to download a "Citizen's Receipt" of their city's spending.

## 🛠️ Tech Stack

- **Backend:** Java (Spring Boot)
- **Database:** PostgreSQL
- **Frontend:** React + Tailwind CSS
- **Visualizations:** D3.js (for custom charts)
- **Data Source:** U.S. Census Bureau 2023 Annual Survey of State and Local Government Finances