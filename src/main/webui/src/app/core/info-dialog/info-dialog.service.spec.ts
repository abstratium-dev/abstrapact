import { TestBed } from '@angular/core/testing';
import { InfoDialogService } from './info-dialog.service';

describe('InfoDialogService', () => {
  let service: InfoDialogService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(InfoDialogService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should start with dialog closed', () => {
    expect(service.state$().isOpen).toBe(false);
  });

  it('should open dialog with config', () => {
    service.show({
      title: 'Test Title',
      message: 'Test Message'
    });

    const state = service.state$();
    expect(state.isOpen).toBe(true);
    expect(state.config?.title).toBe('Test Title');
    expect(state.config?.message).toBe('Test Message');
  });

  it('should use default ok text', () => {
    service.show({
      title: 'Test',
      message: 'Test'
    });

    const state = service.state$();
    expect(state.config?.okText).toBe('OK');
  });

  it('should use custom ok text', () => {
    service.show({
      title: 'Test',
      message: 'Test',
      okText: 'Got it'
    });

    const state = service.state$();
    expect(state.config?.okText).toBe('Got it');
  });

  it('should resolve when acknowledged', async () => {
    const promise = service.show({
      title: 'Test',
      message: 'Test'
    });

    service.handleOk();

    await promise;
    expect(service.state$().isOpen).toBe(false);
  });
});
