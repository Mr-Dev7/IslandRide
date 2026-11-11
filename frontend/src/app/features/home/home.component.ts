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
    <div class="hero-card">
      <h1>Discover & Rent Vehicles in the Andaman Islands</h1>
      <p>Verified self-drive scooters, motorcycles, sedans, SUVs & 4x4 jeeps across Sri Vijaya Puram (Port Blair), Havelock & Neil Island.</p>

      <form class="quick-search" (ngSubmit)="search()">
        <div class="qs-field">
          <label for="home-island">Island</label>
          <select id="home-island" [(ngModel)]="islandId" name="islandId">
            <option [ngValue]="null">All Andaman Islands</option>
            @for (isl of islands(); track isl.id) {
              <option [ngValue]="isl.id">{{ isl.displayAlias || isl.name }}</option>
            }
          </select>
        </div>

        <div class="qs-field">
          <label for="home-cat">Category</label>
          <select id="home-cat" [(ngModel)]="categoryId" name="categoryId">
            <option [ngValue]="null">All Vehicles</option>
            @for (cat of categories(); track cat.id) {
              <option [ngValue]="cat.id">{{ cat.name }}</option>
            }
          </select>
        </div>

        <div class="qs-field">
          <label for="home-start">Pickup</label>
          <input id="home-start" type="datetime-local" [(ngModel)]="startLocal" name="startLocal" />
        </div>

        <div class="qs-field">
          <label for="home-end">Return</label>
          <input id="home-end" type="datetime-local" [(ngModel)]="endLocal" name="endLocal" />
        </div>

        <button type="submit" class="ir-btn ir-btn-secondary qs-btn">Search Fleet</button>
      </form>
    </div>

    <section class="highlights">
      <h2>Popular Categories & Islands</h2>
      <div class="pill-grid">
        @for (isl of islands(); track isl.id) {
          <a routerLink="/search" [queryParams]="{ islandId: isl.id }" class="ir-card pad isl-link">
            <strong>{{ isl.displayAlias || isl.name }}</strong>
            <span class="muted small">{{ isl.description || 'Explore verified fleet on this island' }}</span>
          </a>
        }
      </div>
    </section>
  `,
  styles: [`
    .hero-card { background: linear-gradient(135deg, #1457d9, #0284c7); color: #fff; padding: 2.5rem 2rem; border-radius: var(--radius-lg); text-align: center; }
    .hero-card h1 { font-size: 2rem; margin-bottom: 0.65rem; }
    .hero-card p { font-size: 1.05rem; opacity: 0.92; margin-bottom: 1.5rem; }
    .quick-search { background: #fff; color: #0f172a; padding: 1rem; border-radius: var(--radius-md); display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)) auto; gap: 0.75rem; align-items: end; text-align: left; }
    .qs-field label { display: block; font-size: 0.75rem; font-weight: 700; color: #475569; margin-bottom: 0.25rem; }
    .qs-field input, .qs-field select { width: 100%; padding: 0.5rem 0.65rem; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 0.875rem; }
    .qs-btn { height: 38px; white-space: nowrap; }
    .highlights { margin-top: 2rem; }
    .highlights h2 { font-size: 1.25rem; margin-bottom: 0.85rem; }
    .pill-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1rem; }
    .isl-link { display: flex; flex-direction: column; gap: 0.25rem; color: inherit; }
    .isl-link:hover { border-color: var(--color-primary); text-decoration: none; }
    @media (max-width: 860px) { .quick-search { grid-template-columns: 1fr; } }
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
