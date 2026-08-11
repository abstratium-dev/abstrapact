import { Injectable, signal, Signal } from '@angular/core';

export interface InfoDialogConfig {
  title: string;
  message: string;
  okText?: string;
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
        },
        resolve,
      });
    });
  }

  handleOk(): void {
    const currentState = this.state();
    if (currentState.resolve) {
      currentState.resolve();
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
