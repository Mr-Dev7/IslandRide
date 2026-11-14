import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { RazorpayConstructor } from '../models/razorpay.models';

export const RAZORPAY_SDK_URL = 'https://checkout.razorpay.com/v1/checkout.js';
export const DEFAULT_LOAD_TIMEOUT_MS = 10000;

@Injectable({
  providedIn: 'root',
})
export class RazorpayLoaderService {
  private readonly document = inject(DOCUMENT);
  private loadingPromise: Promise<RazorpayConstructor> | null = null;

  /**
   * Returns true if the Razorpay constructor is already available on the global window.
   */
  isLoaded(): boolean {
    const win = this.getWindow();
    return !!(win && typeof win.Razorpay === 'function');
  }

  /**
   * Dynamically loads the official Razorpay Checkout SDK (checkout.js).
   *
   * - Resolves immediately if the SDK is already present on the window.
   * - If an existing script element is found:
   *   - Resolves immediately if window.Razorpay is already available.
   *   - Rejects immediately if the script already completed loading or failed without making window.Razorpay available.
   *   - Safely observes completion using addEventListener if the script is still loading.
   *   - Pre-existing scripts are NEVER removed from the DOM on failure or timeout.
   * - Only script elements created by this service are removed from the DOM upon failure or timeout.
   * - Avoids overwriting existing event handlers on the script element.
   * - Arms a safety timeout so the Promise cannot remain pending indefinitely.
   * - Shares the in-flight Promise across concurrent callers.
   * - Always cleans up event listeners, timers, and resets cached Promise on failure to permit retries.
   *
   * @param timeoutMs Maximum time in milliseconds to wait for the SDK to load (default: 10000ms).
   */
  load(timeoutMs: number = DEFAULT_LOAD_TIMEOUT_MS): Promise<RazorpayConstructor> {
    const win = this.getWindow();
    if (!win) {
      return Promise.reject(
        new Error('Razorpay Checkout SDK cannot be loaded in a non-browser environment.')
      );
    }

    // 1. If SDK is already available on window, resolve immediately
    if (this.isLoaded()) {
      return Promise.resolve(win.Razorpay!);
    }

    // 2. If a loading operation is already in flight, share the same Promise
    if (this.loadingPromise) {
      return this.loadingPromise;
    }

    // 3. Check for existing script element in the DOM
    const existingScript = this.document.querySelector<HTMLScriptElement>(
      `script[src="${RAZORPAY_SDK_URL}"]`
    );

    if (existingScript) {
      // If the existing script already completed its execution but window.Razorpay is absent
      if (this.isScriptCompleted(existingScript)) {
        return Promise.reject(
          new Error(
            'Razorpay Checkout SDK script downloaded, but window.Razorpay is unavailable.'
          )
        );
      }

      // If the existing script has already failed
      if (this.isScriptFailed(existingScript)) {
        return Promise.reject(
          new Error(
            'Failed to load Razorpay Checkout SDK. Please check your internet connection or ad blocker settings.'
          )
        );
      }
    }

    const isServiceCreated = !existingScript;

    // 4. Initiate dynamic script loading or observe existing in-flight script
    this.loadingPromise = new Promise<RazorpayConstructor>((resolve, reject) => {
      let script = existingScript;

      if (!script) {
        script = this.document.createElement('script');
        script.src = RAZORPAY_SDK_URL;
        script.type = 'text/javascript';
        script.async = true;
        script.setAttribute('data-status', 'loading');
        const targetHead =
          this.document.head ||
          this.document.getElementsByTagName('head')[0] ||
          this.document.body;
        targetHead.appendChild(script);
      }

      let timeoutTimer: ReturnType<typeof setTimeout> | null = null;

      const cleanup = () => {
        if (timeoutTimer) {
          clearTimeout(timeoutTimer);
          timeoutTimer = null;
        }
        if (script) {
          script.removeEventListener('load', onLoad);
          script.removeEventListener('error', onError);
        }
      };

      const onLoad = () => {
        cleanup();
        this.loadingPromise = null;
        if (isServiceCreated && script) {
          script.setAttribute('data-status', 'loaded');
          script.setAttribute('data-loaded', 'true');
        }
        if (this.isLoaded()) {
          resolve(win.Razorpay!);
        } else {
          if (isServiceCreated && script && script.parentNode) {
            script.parentNode.removeChild(script);
          }
          reject(
            new Error(
              'Razorpay Checkout SDK script downloaded, but window.Razorpay is unavailable.'
            )
          );
        }
      };

      const onError = () => {
        cleanup();
        this.loadingPromise = null;
        if (isServiceCreated && script) {
          script.setAttribute('data-status', 'error');
          if (script.parentNode) {
            script.parentNode.removeChild(script);
          }
        }
        reject(
          new Error(
            'Failed to load Razorpay Checkout SDK. Please check your internet connection or ad blocker settings.'
          )
        );
      };

      script.addEventListener('load', onLoad);
      script.addEventListener('error', onError);

      // Arm safety timeout to ensure Promise cannot remain pending indefinitely
      if (timeoutMs > 0) {
        timeoutTimer = setTimeout(() => {
          cleanup();
          this.loadingPromise = null;
          if (isServiceCreated && script && script.parentNode) {
            script.parentNode.removeChild(script);
          }
          reject(
            new Error(
              `Razorpay Checkout SDK loading timed out after ${timeoutMs}ms.`
            )
          );
        }, timeoutMs);
      }
    });

    return this.loadingPromise;
  }

  private isScriptCompleted(script: HTMLScriptElement): boolean {
    const readyState = (script as any).readyState;
    return (
      readyState === 'complete' ||
      readyState === 'loaded' ||
      script.getAttribute('data-loaded') === 'true' ||
      script.getAttribute('data-status') === 'loaded' ||
      script.getAttribute('data-status') === 'complete' ||
      (script as any)._loaded === true
    );
  }

  private isScriptFailed(script: HTMLScriptElement): boolean {
    const readyState = (script as any).readyState;
    return (
      readyState === 'error' ||
      script.getAttribute('data-failed') === 'true' ||
      script.getAttribute('data-status') === 'error' ||
      (script as any)._failed === true
    );
  }

  private getWindow(): Window | null {
    return this.document.defaultView || (typeof window !== 'undefined' ? window : null);
  }
}
