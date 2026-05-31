import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { NotFoundComponent } from './core/not-found/not-found.component';
import { SignedInComponent } from './core/signed-in/signed-in.component';
import { SignedOutComponent } from './core/signed-out/signed-out.component';
import { LegalComponent } from './core/legal/legal.component';
import { ProductDefinitionsListComponent } from './product-definitions/product-definitions-list/product-definitions-list.component';
import { ProductDefinitionFormComponent } from './product-definitions/product-definition-form/product-definition-form.component';
import { ProductDefinitionDetailComponent } from './product-definitions/product-definition-detail/product-definition-detail.component';

export const routes: Routes = [
  { path: 'legal',      component: LegalComponent },

  { path: 'signed-in',  component: SignedInComponent, canActivate: [authGuard] },
  { path: 'signed-out', component: SignedOutComponent },

  // Product Definition routes
  { path: '',               component: ProductDefinitionsListComponent,   canActivate: [authGuard] },
  { path: 'product-definitions',               component: ProductDefinitionsListComponent,   canActivate: [authGuard] },
  { path: 'product-definitions/new',          component: ProductDefinitionFormComponent,  canActivate: [authGuard] },
  { path: 'product-definitions/:id',          component: ProductDefinitionDetailComponent, canActivate: [authGuard] },
  { path: 'product-definitions/:id/edit',     component: ProductDefinitionFormComponent,  canActivate: [authGuard] },

  { path: '**',         component: NotFoundComponent }
];
