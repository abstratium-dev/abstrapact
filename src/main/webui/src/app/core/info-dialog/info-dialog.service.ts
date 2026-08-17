import { Injectable, signal, Signal } from '@angular/core';

export type InfoDialogVariant = 'info' | 'warning';

export interface InfoDialogActionLink {
  text: string;
  action: () => void;
}

export interface InfoDialogConfig {
  title: string;
  message: string;
  okText?: string;
  /**
   * Visual variant of the dialog. `'info'` (default) renders with the standard
   * info styling, `'warning'` renders with warning colours.
   */
  variant?: InfoDialogVariant;
  /**
   * When `false`, the dialog cannot be dismissed by clicking the overlay or
   * the OK button (the OK button is hidden entirely). This is used for
   * blocking warnings where the user must take a specific action (e.g. sign
   * out) instead of just acknowledging the message. Defaults to `true`.
   */
  dismissable?: boolean;
  /**
   * Optional link rendered in the dialog actions area. Typically used to give
   * the user a way out of a non-dismissable dialog (e.g. a "Sign out" link).
   */
  actionLink?: InfoDialogActionLink;
}

interface InfoDialogState {
  isOpen: boolean;
  config: InfoDialogConfig | null;
  resolve: ((value: void) => void) | null;
}

/**
 * Similar to {@link ConfirmDialogService} but for informational messages that
 * only require the user to acknowledge them (single "OK" button, no cancel).
 *
 * Used for example to inform the user that an action cannot be performed
 * because the journal is locked.
 *
 * Supports a `warning` variant with a non-dismissable mode and an optional
 * action link, used for example to tell an authenticated user with no roles
 * that they need to contact their administrator and sign out.
 */
@Injectable({
  providedIn: 'root',
})
export class InfoDialogService {
  private state = signal<InfoDialogState>({
    isOpen: false,
    config: null,
    resolve: null,
  });

  state$: Signal<InfoDialogState> = this.state.asReadonly();

  show(config: InfoDialogConfig): Promise<void> {
    return new Promise((resolve) => {
      this.state.set({
        isOpen: true,
        config: {
          ...config,
          okText: config.okText || 'OK',
          variant: config.variant || 'info',
          dismissable: config.dismissable !== false,
        },
        resolve,
      });
    });
  }

  handleOk(): void {
    const currentState = this.state();
    if (!currentState.config || currentState.config.dismissable === false) {
      return;
    }
    if (currentState.resolve) {
      currentState.resolve();
    }
    this.close();
  }

  /**
   * Invoke the configured action link and close the dialog. Used by the
   * dialog component when the user clicks the action link.
   */
  handleActionLink(): void {
    const currentState = this.state();
    const actionLink = currentState.config?.actionLink;
    if (actionLink) {
      actionLink.action();
    }
    this.close();
  }

  private close(): void {
    this.state.set({
      isOpen: false,
      config: null,
      resolve: null,
    });
  }
}
