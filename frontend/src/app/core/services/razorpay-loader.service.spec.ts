import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { RAZORPAY_SDK_URL, RazorpayLoaderService } from './razorpay-loader.service';
import { RazorpayConstructor } from '../models/razorpay.models';

describe('RazorpayLoaderService', () => {
  let service: RazorpayLoaderService;
  let document: Document;

  function cleanupDomAndWindow(): void {
    if (document) {
      const scripts = document.querySelectorAll<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
      scripts.forEach((s) => s.parentNode?.removeChild(s));
    }
    delete (window as any).Razorpay;
    (window as any).Razorpay = undefined;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [RazorpayLoaderService],
    });
    service = TestBed.inject(RazorpayLoaderService);
    document = TestBed.inject(DOCUMENT);
    cleanupDomAndWindow();
  });

  afterEach(() => {
    cleanupDomAndWindow();
  });

  it('should be created and initial isLoaded should be false', () => {
    expect(service).toBeTruthy();
    expect(service.isLoaded()).toBeFalse();
  });

  it('1. should successfully load the SDK and resolve the constructor when load event fires and Razorpay is present', async () => {
    const mockConstructor: RazorpayConstructor = function MockRazorpay(options: any) {
      return {
        open: jasmine.createSpy('open'),
        close: jasmine.createSpy('close'),
        on: jasmine.createSpy('on'),
      };
    } as any;

    const loadPromise = service.load();

    const script = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(script).withContext('Script element must be added to document').not.toBeNull();
    expect(script?.async).toBeTrue();

    // Simulate SDK execution making window.Razorpay available, then trigger load
    (window as any).Razorpay = mockConstructor;
    script?.dispatchEvent(new Event('load'));

    const resolved = await loadPromise;
    expect(resolved).toBe(mockConstructor);
    expect(service.isLoaded()).toBeTrue();
    expect(script?.getAttribute('data-loaded')).toBe('true');
  });

  it('2. should not insert duplicate script elements on multiple load calls', () => {
    service.load();
    service.load();
    service.load();

    const scripts = document.querySelectorAll<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(scripts.length).toBe(1);
  });

  it('3. should reuse the same in-progress loading operation for concurrent callers', () => {
    const p1 = service.load();
    const p2 = service.load();
    const p3 = service.load();

    expect(p1).toBe(p2);
    expect(p2).toBe(p3);
  });

  it('4. should handle an SDK that is already available without inserting any script elements', async () => {
    const preloadedConstructor: RazorpayConstructor = function Preloaded(options: any) {
      return {} as any;
    } as any;
    (window as any).Razorpay = preloadedConstructor;

    expect(service.isLoaded()).toBeTrue();

    const resolved = await service.load();
    expect(resolved).toBe(preloadedConstructor);

    const scripts = document.querySelectorAll<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(scripts.length).toBe(0);
  });

  it('5. should remove a service-created script from the DOM after failure', async () => {
    const loadPromise = service.load();

    const script = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(script).not.toBeNull();

    // Trigger error
    script?.dispatchEvent(new Event('error'));

    await expectAsync(loadPromise).toBeRejectedWithError(
      /Failed to load Razorpay Checkout SDK/
    );
    expect(service.isLoaded()).toBeFalse();

    // Verify service-created script was removed from DOM
    const remainingScript = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(remainingScript).toBeNull();
  });

  it('6. should allow retrying after a failed load and resolve on subsequent successful attempt', async () => {
    // 1st attempt fails
    const failPromise = service.load();
    const failScript = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    failScript?.dispatchEvent(new Event('error'));
    await expectAsync(failPromise).toBeRejected();

    // 2nd attempt retries fresh
    const retryConstructor: RazorpayConstructor = function RetryMock(options: any) {
      return {} as any;
    } as any;

    const retryPromise = service.load();
    const retryScript = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(retryScript).withContext('A new script tag must be inserted on retry').not.toBeNull();
    expect(retryScript).not.toBe(failScript);

    (window as any).Razorpay = retryConstructor;
    retryScript?.dispatchEvent(new Event('load'));

    const resolved = await retryPromise;
    expect(resolved).toBe(retryConstructor);
    expect(service.isLoaded()).toBeTrue();
  });

  it('7. should remove a service-created script when the SDK loads but window.Razorpay is unavailable', async () => {
    const loadPromise = service.load();

    const script = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(script).not.toBeNull();

    // Simulate script load where window.Razorpay remains undefined (e.g. adblocker stub or blocked content)
    delete (window as any).Razorpay;
    (window as any).Razorpay = undefined;
    script?.dispatchEvent(new Event('load'));

    await expectAsync(loadPromise).toBeRejectedWithError(
      /window\.Razorpay is unavailable/
    );
    expect(service.isLoaded()).toBeFalse();

    // Verify bad service-created script tag was removed from DOM
    const remainingScript = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(remainingScript).toBeNull();
  });

  it('8. should reject when an existing script has already completed loading but Razorpay is unavailable, keeping the script in DOM', async () => {
    const existingScript = document.createElement('script');
    existingScript.src = RAZORPAY_SDK_URL;
    existingScript.setAttribute('data-loaded', 'true');
    document.head.appendChild(existingScript);

    delete (window as any).Razorpay;
    (window as any).Razorpay = undefined;

    await expectAsync(service.load()).toBeRejectedWithError(
      /Razorpay Checkout SDK script downloaded, but window\.Razorpay is unavailable/
    );
    expect(service.isLoaded()).toBeFalse();

    // Verify the pre-existing script element was preserved in DOM
    const remaining = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(remaining).toBe(existingScript);
  });

  it('9. should safely observe and resolve when an existing script is still loading', async () => {
    const existingScript = document.createElement('script');
    existingScript.src = RAZORPAY_SDK_URL;
    existingScript.async = true;
    document.head.appendChild(existingScript);

    const mockConstructor: RazorpayConstructor = function MockRazorpay(options: any) {
      return {
        open: jasmine.createSpy('open'),
      };
    } as any;

    const loadPromise = service.load();

    // Verify no duplicate script was inserted
    const scripts = document.querySelectorAll<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(scripts.length).toBe(1);
    expect(scripts[0]).toBe(existingScript);

    // Simulate existing script finishing its load
    (window as any).Razorpay = mockConstructor;
    existingScript.dispatchEvent(new Event('load'));

    const resolved = await loadPromise;
    expect(resolved).toBe(mockConstructor);
    expect(service.isLoaded()).toBeTrue();
  });

  it('10. should not overwrite unrelated event listeners or onload handlers on existing scripts', async () => {
    const existingScript = document.createElement('script');
    existingScript.src = RAZORPAY_SDK_URL;

    const externalEventListenerSpy = jasmine.createSpy('externalEventListener');
    existingScript.addEventListener('load', externalEventListenerSpy);

    const externalOnLoadSpy = jasmine.createSpy('externalOnLoad');
    existingScript.onload = externalOnLoadSpy;

    document.head.appendChild(existingScript);

    const mockConstructor: RazorpayConstructor = function MockRazorpay(options: any) {
      return {} as any;
    } as any;

    const loadPromise = service.load();

    // Ensure onload property was not overwritten by service
    expect(existingScript.onload).toBe(externalOnLoadSpy);

    // Simulate load event
    (window as any).Razorpay = mockConstructor;
    existingScript.dispatchEvent(new Event('load'));
    existingScript.onload(new Event('load') as any);

    const resolved = await loadPromise;
    expect(resolved).toBe(mockConstructor);
    expect(externalEventListenerSpy).toHaveBeenCalled();
    expect(externalOnLoadSpy).toHaveBeenCalled();
  });

  it('11. should remove a service-created script when loading times out and allow subsequent retry', async () => {
    jasmine.clock().install();
    try {
      const timeoutPromise = service.load(500);
      jasmine.clock().tick(501);

      await expectAsync(timeoutPromise).toBeRejectedWithError(
        /timed out after 500ms/i
      );
      expect(service.isLoaded()).toBeFalse();

      // Verify the service-created script was removed on timeout
      const remainingScript = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
      expect(remainingScript).toBeNull();
    } finally {
      jasmine.clock().uninstall();
    }

    // 2nd attempt retries and succeeds
    const retryConstructor: RazorpayConstructor = function RetryMock() {
      return {} as any;
    } as any;

    const retryPromise = service.load();
    const retryScript = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(retryScript).withContext('A new script element should be created on retry').not.toBeNull();

    (window as any).Razorpay = retryConstructor;
    retryScript?.dispatchEvent(new Event('load'));

    const resolved = await retryPromise;
    expect(resolved).toBe(retryConstructor);
    expect(service.isLoaded()).toBeTrue();
  });

  it('12. should keep a pre-existing external script in the DOM after timeout and allow retry', async () => {
    const externalScript = document.createElement('script');
    externalScript.src = RAZORPAY_SDK_URL;
    document.head.appendChild(externalScript);

    jasmine.clock().install();
    try {
      const timeoutPromise = service.load(500);
      jasmine.clock().tick(501);

      await expectAsync(timeoutPromise).toBeRejectedWithError(
        /timed out after 500ms/i
      );
      expect(service.isLoaded()).toBeFalse();

      // Verify pre-existing script remains in the DOM
      const remainingScript = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
      expect(remainingScript).toBe(externalScript);
    } finally {
      jasmine.clock().uninstall();
    }

    // 2nd attempt retries on the existing script and succeeds
    const retryConstructor: RazorpayConstructor = function RetryMock() {
      return {} as any;
    } as any;

    const retryPromise = service.load();
    (window as any).Razorpay = retryConstructor;
    externalScript.dispatchEvent(new Event('load'));

    const resolved = await retryPromise;
    expect(resolved).toBe(retryConstructor);
    expect(service.isLoaded()).toBeTrue();
  });

  it('13. should keep a pre-existing external script in the DOM after an error', async () => {
    const externalScript = document.createElement('script');
    externalScript.src = RAZORPAY_SDK_URL;
    document.head.appendChild(externalScript);

    const loadPromise = service.load();

    // Trigger error on external script
    externalScript.dispatchEvent(new Event('error'));

    await expectAsync(loadPromise).toBeRejectedWithError(
      /Failed to load Razorpay Checkout SDK/
    );
    expect(service.isLoaded()).toBeFalse();

    // Verify pre-existing script remains in the DOM
    const remainingScript = document.querySelector<HTMLScriptElement>(`script[src="${RAZORPAY_SDK_URL}"]`);
    expect(remainingScript).toBe(externalScript);
  });
});
