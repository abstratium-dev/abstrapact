import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { NotFoundComponent } from './core/not-found/not-found.component';
import { DemoComponent } from './demo/demo.component';
import { TodoComponent } from './demo/todo.component';
import { SignedInComponent } from './core/signed-in/signed-in.component';
import { SignedOutComponent } from './core/signed-out/signed-out.component';
import { PublicComponent } from './demo/public.component';

export const routes: Routes = [
  { path: 'public',     component: PublicComponent },
  { path: '',           component: PublicComponent },
  { path: 'demo',       component: DemoComponent,     canActivate: [authGuard] },
  { path: 'TODO',       component: TodoComponent,     canActivate: [authGuard] },


  { path: 'signed-in',  component: SignedInComponent, canActivate: [authGuard] },
  { path: 'signed-out', component: SignedOutComponent },
  { path: '**',         component: NotFoundComponent }
];
