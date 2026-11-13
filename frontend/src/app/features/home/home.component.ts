import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CatalogService } from '../../core/services/catalog.service';
import { IslandDto, VehicleCategoryDto } from '../../core/models/vehicle.models';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="hero-container">
      <div class="hero-card">
        <div class="hero-tag">Verified Island Fleet Marketplace</div>
        <h1>Explore the Andaman Islands on Your Terms</h1>
        <p class="hero-sub">
          Self-drive scooters, motorcycles, sedans & 4x4 jeeps across Sri Vijaya Puram (Port Blair), Swaraj Dweep (Havelock) & Shaheed Dweep (Neil).
        </p>

        <!-- Quick Island Selection Pills -->
        <div class="island-pills">
          @for (isl of islands(); track isl.id) {
            <button
              type="button"
              class="island-pill"
              [class.active]="islandId === isl.id"
              (click)="selectIsland(isl.id)"
            >
              <span class="pill-dot"></span>
              {{ isl.displayAlias || isl.name }}
            </button>
          }
        </div>

        <form class="quick-search" (ngSubmit)="search()">
          <div class="qs-field">
            <label for="home-island">Island</label>
            <select id="home-island" [(ngModel)]="islandId" name="islandId" class="qs-input">
              <option [ngValue]="null">All Andaman Islands</option>
              @for (isl of islands(); track isl.id) {
                <option [ngValue]="isl.id">{{ isl.displayAlias || isl.name }}</option>
              }
            </select>
          </div>

          <div class="qs-field">
            <label for="home-cat">Vehicle Category</label>
            <select id="home-cat" [(ngModel)]="categoryId" name="categoryId" class="qs-input">
              <option [ngValue]="null">All Vehicle Types</option>
              @for (cat of categories(); track cat.id) {
                <option [ngValue]="cat.id">{{ cat.name }}</option>
              }
            </select>
          </div>

          <div class="qs-field">
            <label for="home-start">Pickup Date & Time</label>
            <input id="home-start" type="datetime-local" [(ngModel)]="startLocal" name="startLocal" class="qs-input" />
          </div>

          <div class="qs-field">
            <label for="home-end">Return Date & Time</label>
            <input id="home-end" type="datetime-local" [(ngModel)]="endLocal" name="endLocal" class="qs-input" />
          </div>

          <div class="qs-submit">
            <button type="submit" class="ir-btn ir-btn-primary qs-btn">Search Fleet</button>
          </div>
        </form>
      </div>
    </div>

    <!-- Trust Highlights Bar -->
    <div class="trust-bar">
      <div class="trust-item">
        <div class="trust-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
        </div>
        <div>
          <strong>Ring-Fenced Security Deposits</strong>
          <p>Protected in dedicated trust escrow, released promptly post-trip.</p>
        </div>
      </div>
      <div class="trust-item">
        <div class="trust-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
        </div>
        <div>
          <strong>Ferry & Weather Protection</strong>
          <p>Full refunds or priority reschedule if cyclone or DSS ferry delays strike.</p>
        </div>
      </div>
      <div class="trust-item">
        <div class="trust-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        </div>
        <div>
          <strong>Zero Hidden Counter Fees</strong>
          <p>Transparent quotes with GST & helmet included. No jetty surcharges.</p>
        </div>
      </div>
    </div>

    <!-- Island Guides -->
    <section class="highlights">
      <div class="section-title-wrap">
        <h2>Rentals Across Andaman Hubs</h2>
        <p class="section-sub">Pick up your ride right at airport terminals, ferry jetties, or hotel deliver hubs</p>
      </div>

      <div class="island-grid">
        @for (isl of islands(); track isl.id) {
          <a routerLink="/search" [queryParams]="{ islandId: isl.id }" class="island-card">
            <div class="island-card-top">
              <h3>{{ isl.displayAlias || isl.name }}</h3>
              <span class="view-pill">Explore &rarr;</span>
            </div>
            <p class="island-desc">{{ isl.description || 'Verified local partner hubs and direct jetty pickup available.' }}</p>
          </a>
        }
      </div>
    </section>
  `,
  styles: [`
    .hero-container {
      margin-bottom: 2rem;
    }
    .hero-card {
      background: linear-gradient(145deg, var(--ir-navy) 0%, #1a4f62 60%, var(--ir-teal) 140%);
      color: #ffffff;
      padding: 3rem 2.5rem;
      border-radius: var(--ir-radius-xl);
      text-align: center;
      box-shadow: var(--ir-shadow-lg);
      position: relative;
      overflow: hidden;
    }
    .hero-tag {
      display: inline-block;
      background: rgba(22, 184, 166, 0.2);
      color: #5eead4;
      border: 1px solid rgba(22, 184, 166, 0.4);
      padding: 0.25rem 0.875rem;
      border-radius: var(--ir-radius-full);
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      margin-bottom: 1rem;
    }
    .hero-card h1 {
      font-size: 2.25rem;
      font-weight: 800;
      line-height: 1.2;
      margin: 0 0 0.75rem 0;
      color: #ffffff;
      letter-spacing: -0.025em;
    }
    .hero-sub {
      font-size: 1.0625rem;
      color: #e2e8f0;
      max-width: 680px;
      margin: 0 auto 1.75rem auto;
      line-height: 1.5;
    }

    .island-pills {
      display: flex;
      justify-content: center;
      gap: 0.625rem;
      margin-bottom: 1.75rem;
      flex-wrap: wrap;
    }
    .island-pill {
      background: rgba(255, 255, 255, 0.12);
      color: #ffffff;
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: var(--ir-radius-full);
      padding: 0.4rem 1rem;
      font-size: 0.8125rem;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      transition: all var(--ir-transition);
      min-height: 36px;
    }
    .island-pill:hover {
      background: rgba(255, 255, 255, 0.22);
      border-color: rgba(255, 255, 255, 0.4);
    }
    .island-pill.active {
      background: var(--ir-teal);
      border-color: var(--ir-teal);
      color: #ffffff;
      box-shadow: 0 2px 8px rgba(22, 184, 166, 0.4);
    }
    .pill-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: currentColor;
    }

    .quick-search {
      background: var(--ir-surface);
      color: var(--ir-charcoal);
      padding: 1.25rem 1.5rem;
      border-radius: var(--ir-radius-lg);
      display: grid;
      grid-template-columns: repeat(4, 1fr) auto;
      gap: 1rem;
      align-items: end;
      text-align: left;
      box-shadow: var(--ir-shadow-md);
      border: 1px solid var(--ir-border);
    }
    .qs-field {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .qs-field label {
      font-size: 0.75rem;
      font-weight: 700;
      color: var(--ir-navy);
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }
    .qs-input {
      width: 100%;
      padding: 0.55rem 0.75rem;
      border: 1px solid var(--ir-border);
      border-radius: var(--ir-radius-sm);
      font-size: 0.875rem;
      background: #ffffff;
      color: var(--ir-charcoal);
      height: 42px;
      box-sizing: border-box;
    }
    .qs-input:focus {
      outline: none;
      border-color: var(--ir-teal);
      box-shadow: 0 0 0 3px rgba(22, 184, 166, 0.15);
    }
    .qs-submit {
      display: flex;
      align-items: flex-end;
    }
    .qs-btn {
      height: 42px;
      white-space: nowrap;
      padding: 0 1.5rem;
      font-weight: 700;
      width: 100%;
    }

    @media (max-width: 992px) {
      .quick-search {
        grid-template-columns: 1fr 1fr;
      }
      .qs-submit {
        grid-column: span 2;
      }
    }
    @media (max-width: 600px) {
      .hero-card {
        padding: 2rem 1.25rem;
      }
      .hero-card h1 {
        font-size: 1.75rem;
      }
      .quick-search {
        grid-template-columns: 1fr;
      }
      .qs-submit {
        grid-column: 1;
      }
    }

    /* Trust Highlights Bar */
    .trust-bar {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 1.5rem;
      margin-bottom: 2.5rem;
    }
    @media (max-width: 768px) {
      .trust-bar {
        grid-template-columns: 1fr;
        gap: 1rem;
      }
    }
    .trust-item {
      display: flex;
      align-items: flex-start;
      gap: 0.875rem;
      padding: 1.25rem;
      background: var(--ir-surface);
      border-radius: var(--ir-radius-md);
      border: 1px solid var(--ir-border);
      box-shadow: var(--ir-shadow-sm);
    }
    .trust-icon {
      width: 40px;
      height: 40px;
      border-radius: var(--ir-radius-sm);
      background: var(--ir-sea-mist);
      color: var(--ir-navy);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .trust-icon svg {
      width: 20px;
      height: 20px;
    }
    .trust-item strong {
      display: block;
      font-size: 0.9375rem;
      color: var(--ir-navy);
      margin-bottom: 0.25rem;
    }
    .trust-item p {
      font-size: 0.8125rem;
      color: var(--ir-muted);
      margin: 0;
      line-height: 1.4;
    }

    /* Highlights Section */
    .highlights {
      margin-bottom: 2.5rem;
    }
    .section-title-wrap {
      margin-bottom: 1.25rem;
    }
    .section-title-wrap h2 {
      font-size: 1.375rem;
      font-weight: 700;
      color: var(--ir-navy);
      margin: 0 0 0.25rem 0;
    }
    .section-sub {
      color: var(--ir-muted);
      font-size: 0.875rem;
      margin: 0;
    }
    .island-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 1.25rem;
    }
    .island-card {
      background: var(--ir-surface);
      border: 1px solid var(--ir-border);
      border-radius: var(--ir-radius-md);
      padding: 1.5rem;
      text-decoration: none;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      transition: all var(--ir-transition);
      box-shadow: var(--ir-shadow-sm);
    }
    .island-card:hover {
      border-color: var(--ir-teal);
      transform: translateY(-2px);
      box-shadow: var(--ir-shadow-md);
    }
    .island-card-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .island-card h3 {
      font-size: 1.125rem;
      font-weight: 700;
      color: var(--ir-navy);
      margin: 0;
    }
    .view-pill {
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--ir-teal);
    }
    .island-desc {
      font-size: 0.8125rem;
      color: var(--ir-muted);
      margin: 0;
      line-height: 1.4;
    }
  `]
})
export class HomeComponent implements OnInit {
  private readonly catalogService = inject(CatalogService);
  private readonly router = inject(Router);

  islands = signal<IslandDto[]>([]);
  categories = signal<VehicleCategoryDto[]>([]);

  islandId: number | null = null;
  categoryId: number | null = null;
  startLocal = '';
  endLocal = '';

  ngOnInit(): void {
    this.catalogService.getIslands().subscribe({ next: (d) => this.islands.set(d) });
    this.catalogService.getCategories().subscribe({ next: (d) => this.categories.set(d) });
  }

  selectIsland(id: number): void {
    this.islandId = this.islandId === id ? null : id;
  }

  search(): void {
    this.router.navigate(['/search'], {
      queryParams: {
        islandId: this.islandId || null,
        categoryId: this.categoryId || null,
        startLocal: this.startLocal || null,
        endLocal: this.endLocal || null,
      },
    });
  }
}
