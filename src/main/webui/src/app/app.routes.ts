import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { NotFoundComponent } from './core/not-found/not-found.component';
import { SignedInComponent } from './core/signed-in/signed-in.component';
import { SignedOutComponent } from './core/signed-out/signed-out.component';
import { LegalComponent } from './core/legal/legal.component';
import { ProductDefinitionsListComponent } from './product-definitions/product-definitions-list/product-definitions-list.component';
import { ProductDefinitionFormComponent } from './product-definitions/product-definition-form/product-definition-form.component';
import { ProductDefinitionDetailComponent } from './product-definitions/product-definition-detail/product-definition-detail.component';
import { ProductSimulatorComponent } from './product-definitions/product-simulator/product-simulator.component';
import { TermsAndConditionsListComponent } from './terms-and-conditions/terms-and-conditions-list/terms-and-conditions-list.component';
import { TermsAndConditionsFormComponent } from './terms-and-conditions/terms-and-conditions-form/terms-and-conditions-form.component';
import { TermsAndConditionsDetailComponent } from './terms-and-conditions/terms-and-conditions-detail/terms-and-conditions-detail.component';

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
  { path: 'product-definitions/:id/simulate', component: ProductSimulatorComponent, canActivate: [authGuard] },

  // Terms and Conditions routes
  { path: 'terms-and-conditions',               component: TermsAndConditionsListComponent,   canActivate: [authGuard] },
  { path: 'terms-and-conditions/new',           component: TermsAndConditionsFormComponent, canActivate: [authGuard] },
  { path: 'terms-and-conditions/:id',           component: TermsAndConditionsDetailComponent, canActivate: [authGuard] },
  { path: 'terms-and-conditions/:id/edit',      component: TermsAndConditionsFormComponent,   canActivate: [authGuard] },

  { path: '**',         component: NotFoundComponent }
];
