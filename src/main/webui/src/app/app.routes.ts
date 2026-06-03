import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { ProductDefinitionsListComponent } from './product-definitions/product-definitions-list/product-definitions-list.component';

export const routes: Routes = [
  { path: 'legal', loadComponent: () => import('./core/legal/legal.component').then(m => m.LegalComponent) },

  { path: 'signed-in', loadComponent: () => import('./core/signed-in/signed-in.component').then(m => m.SignedInComponent), canActivate: [authGuard] },
  { path: 'signed-out', loadComponent: () => import('./core/signed-out/signed-out.component').then(m => m.SignedOutComponent) },

  // Product Definition routes
  { path: '', component: ProductDefinitionsListComponent, canActivate: [authGuard] },
  { path: 'product-definitions', loadComponent: () => import('./product-definitions/product-definitions-list/product-definitions-list.component').then(m => m.ProductDefinitionsListComponent), canActivate: [authGuard] },
  { path: 'product-definitions/new', loadComponent: () => import('./product-definitions/product-definition-form/product-definition-form.component').then(m => m.ProductDefinitionFormComponent), canActivate: [authGuard] },
  { path: 'product-definitions/:id', loadComponent: () => import('./product-definitions/product-definition-detail/product-definition-detail.component').then(m => m.ProductDefinitionDetailComponent), canActivate: [authGuard] },
  { path: 'product-definitions/:id/edit', loadComponent: () => import('./product-definitions/product-definition-form/product-definition-form.component').then(m => m.ProductDefinitionFormComponent), canActivate: [authGuard] },
  { path: 'product-definitions/:id/simulate', loadComponent: () => import('./product-definitions/product-simulator/product-simulator.component').then(m => m.ProductSimulatorComponent), canActivate: [authGuard] },

  // Config route
  { path: 'config', loadComponent: () => import('./config/config.component').then(m => m.ConfigComponent), canActivate: [authGuard] },

  // Terms and Conditions routes
  { path: 'terms-and-conditions', loadComponent: () => import('./terms-and-conditions/terms-and-conditions-list/terms-and-conditions-list.component').then(m => m.TermsAndConditionsListComponent), canActivate: [authGuard] },
  { path: 'terms-and-conditions/new', loadComponent: () => import('./terms-and-conditions/terms-and-conditions-form/terms-and-conditions-form.component').then(m => m.TermsAndConditionsFormComponent), canActivate: [authGuard] },
  { path: 'terms-and-conditions/:id', loadComponent: () => import('./terms-and-conditions/terms-and-conditions-detail/terms-and-conditions-detail.component').then(m => m.TermsAndConditionsDetailComponent), canActivate: [authGuard] },
  { path: 'terms-and-conditions/:id/edit', loadComponent: () => import('./terms-and-conditions/terms-and-conditions-form/terms-and-conditions-form.component').then(m => m.TermsAndConditionsFormComponent), canActivate: [authGuard] },

  { path: '**', loadComponent: () => import('./core/not-found/not-found.component').then(m => m.NotFoundComponent) }
];
