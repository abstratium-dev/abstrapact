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

  it('should default variant to info', () => {
    service.show({ title: 'Test', message: 'Test' });
    expect(service.state$().config?.variant).toBe('info');
  });

  it('should store warning variant when provided', () => {
    service.show({ title: 'Test', message: 'Test', variant: 'warning' });
    expect(service.state$().config?.variant).toBe('warning');
  });

  it('should default dismissable to true', () => {
    service.show({ title: 'Test', message: 'Test' });
    expect(service.state$().config?.dismissable).toBe(true);
  });

  it('should store dismissable false when provided', () => {
    service.show({ title: 'Test', message: 'Test', dismissable: false });
    expect(service.state$().config?.dismissable).toBe(false);
  });

  it('should store action link when provided', () => {
    const action = () => {};
    service.show({ title: 'Test', message: 'Test', actionLink: { text: 'Sign out', action } });
    expect(service.state$().config?.actionLink?.text).toBe('Sign out');
    expect(service.state$().config?.actionLink?.action).toBe(action);
  });

  it('should NOT close when handleOk called on a non-dismissable dialog', () => {
    service.show({ title: 'Test', message: 'Test', dismissable: false });
    service.handleOk();
    expect(service.state$().isOpen).toBe(true);
  });

  it('should close when handleOk called on a dismissable dialog', () => {
    service.show({ title: 'Test', message: 'Test' });
    service.handleOk();
    expect(service.state$().isOpen).toBe(false);
  });

  it('should invoke the action link callback and close on handleActionLink', () => {
    const action = jasmine.createSpy('action');
    service.show({
      title: 'No roles',
      message: 'Contact your administrator',
      dismissable: false,
      actionLink: { text: 'Sign out', action },
    });

    service.handleActionLink();

    expect(action).toHaveBeenCalledTimes(1);
    expect(service.state$().isOpen).toBe(false);
  });

  it('should close on handleActionLink even with no action link configured', () => {
    service.show({ title: 'Test', message: 'Test' });
    service.handleActionLink();
    expect(service.state$().isOpen).toBe(false);
  });
});
