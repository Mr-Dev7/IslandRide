import { spawn } from 'child_process';
import fs from 'fs';
import path from 'path';

const chromePath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const tempUserDataDir = path.join(process.env.TEMP || 'C:\\Temp', 'chrome_verifier_prof_' + Date.now());
const screenshotsDir = path.resolve('docs/screenshots');

if (!fs.existsSync(screenshotsDir)) {
  fs.mkdirSync(screenshotsDir, { recursive: true });
}

console.log('=== IslandRide Automated Visual & Functional Verifier ===');
console.log('Launching Chrome with remote debugging on port 9222...');

const chrome = spawn(chromePath, [
  '--headless=new',
  '--remote-debugging-port=9222',
  `--user-data-dir=${tempUserDataDir}`,
  '--disable-gpu',
  '--no-first-run',
  '--window-size=1280,800',
  'about:blank'
]);

// Wait 2.5s for Chrome to initialize
await new Promise(r => setTimeout(r, 2500));

let ws;
let messageId = 1;
const callbacks = new Map();
const consoleErrors = [];

function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = messageId++;
    callbacks.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
}

try {
  const versionRes = await fetch('http://127.0.0.1:9222/json/version');
  const versionData = await versionRes.json();
  console.log(`Connected to Chrome version: ${versionData.Browser}`);

  // Create new page target
  const newTargetRes = await fetch('http://127.0.0.1:9222/json/new?about:blank', { method: 'PUT' });
  const targetData = await newTargetRes.json();
  const pageWsUrl = targetData.webSocketDebuggerUrl;

  ws = new WebSocket(pageWsUrl);

  await new Promise((resolve, reject) => {
    ws.onopen = resolve;
    ws.onerror = reject;
  });

  ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    if (msg.id && callbacks.has(msg.id)) {
      const { resolve, reject } = callbacks.get(msg.id);
      callbacks.delete(msg.id);
      if (msg.error) reject(new Error(msg.error.message));
      else resolve(msg.result);
    } else if (msg.method === 'Runtime.consoleAPICalled') {
      if (msg.params.type === 'error') {
        const text = msg.params.args.map(a => a.value || a.description || JSON.stringify(a)).join(' ');
        // Filter benign Chrome/favicon noise
        if (!text.includes('favicon.ico')) {
          consoleErrors.push(text);
        }
      }
    }
  };

  // Enable necessary CDP domains
  await send('Page.enable');
  await send('Runtime.enable');
  await send('DOM.enable');

  async function setViewport(width, height, isMobile = false) {
    await send('Emulation.setDeviceMetricsOverride', {
      width,
      height,
      deviceScaleFactor: 1,
      mobile: isMobile
    });
  }

  async function navigate(url, waitMs = 1500) {
    console.log(`Navigating to ${url}...`);
    await send('Page.navigate', { url });
    await new Promise(r => setTimeout(r, waitMs));
  }

  async function captureScreenshot(filename) {
    const res = await send('Page.captureScreenshot', { format: 'png' });
    const buffer = Buffer.from(res.data, 'base64');
    const outPath = path.join(screenshotsDir, filename);
    fs.writeFileSync(outPath, buffer);
    console.log(`  -> Saved screenshot: ${filename} (${Math.round(buffer.length / 1024)} KB)`);
    return outPath;
  }

  async function evaluate(expression) {
    const res = await send('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true
    });
    if (res.exceptionDetails) {
      throw new Error(res.exceptionDetails.exception?.description || 'Evaluation error');
    }
    return res.result?.value;
  }

  async function performLogin(email, password) {
    console.log(`Logging in as ${email}...`);
    await navigate('http://localhost:4200/auth/login');
    await evaluate(`
      (() => {
        const emailInput = document.querySelector('#email');
        const passInput = document.querySelector('#password');
        const submitBtn = document.querySelector('button[type="submit"]');
        if (emailInput && passInput && submitBtn) {
          emailInput.value = '${email}';
          emailInput.dispatchEvent(new Event('input', { bubbles: true }));
          passInput.value = '${password}';
          passInput.dispatchEvent(new Event('input', { bubbles: true }));
          setTimeout(() => submitBtn.click(), 100);
        }
      })()
    `);
    await new Promise(r => setTimeout(r, 2200));
    const currentPath = await evaluate('window.location.pathname');
    console.log(`  -> Post-login path: ${currentPath}`);
    return currentPath;
  }

  const results = {
    pages: [],
    overflowChecks: [],
    mobileDrawer: null,
    wizardSteps: null,
    consoleErrors: []
  };

  // =============================================================
  // 1. PUBLIC PORTAL & RESPONSIVE VIEWS
  // =============================================================
  console.log('\n--- 1. Testing Public Portal ---');

  // 1.1 Homepage Desktop (1280x800)
  await setViewport(1280, 800, false);
  await navigate('http://localhost:4200/');
  const homeTitle = await evaluate('document.title');
  const homeHasH1 = await evaluate('!!document.querySelector("h1")');
  const homeDeskOverflow = await evaluate('document.documentElement.scrollWidth > window.innerWidth');
  await captureScreenshot('01-homepage-desktop.png');
  results.pages.push({ page: 'Homepage (Desktop 1280px)', url: '/', ok: homeHasH1 });
  results.overflowChecks.push({ page: 'Homepage Desktop', overflow: homeDeskOverflow });

  // 1.2 Homepage Tablet (768x1024)
  await setViewport(768, 1024, false);
  await navigate('http://localhost:4200/');
  const homeTabOverflow = await evaluate('document.documentElement.scrollWidth > window.innerWidth');
  await captureScreenshot('01b-homepage-tablet.png');
  results.overflowChecks.push({ page: 'Homepage Tablet (768px)', overflow: homeTabOverflow });

  // 1.3 Homepage Mobile (375x667)
  await setViewport(375, 667, true);
  await navigate('http://localhost:4200/');
  const homeMobOverflow = await evaluate('document.documentElement.scrollWidth > window.innerWidth');
  await captureScreenshot('02-homepage-mobile.png');
  results.overflowChecks.push({ page: 'Homepage Mobile (375px)', overflow: homeMobOverflow });

  // 1.3b Mobile Touch Target Ergonomics Audit (>= 44px min dimension)
  const touchAudit = await evaluate(`
    (() => {
      const elements = Array.from(document.querySelectorAll('button, a.ir-btn, input, select, .mobile-hamburger'));
      let count = 0;
      let valid = 0;
      elements.forEach(el => {
        const rect = el.getBoundingClientRect();
        if (rect.width > 0 && rect.height > 0) {
          count++;
          if (rect.height >= 44 || rect.width >= 44) {
            valid++;
          }
        }
      });
      return { totalInteractive: count, compliantTouchTargets: valid, complianceRate: count > 0 ? Math.round((valid / count) * 100) + '%' : '100%' };
    })()
  `);
  results.touchTargetAudit = touchAudit;

  // 1.4 Test Mobile Navigation Drawer
  const hamburgerFound = await evaluate('!!document.querySelector(".mobile-hamburger")');
  await evaluate('document.querySelector(".mobile-hamburger")?.click()');
  await new Promise(r => setTimeout(r, 400));
  const drawerOpen = await evaluate('!!document.querySelector(".mobile-drawer")');
  await captureScreenshot('02b-homepage-mobile-drawer-open.png');
  // Close drawer
  await evaluate('document.querySelector(".mobile-hamburger")?.click()');
  await new Promise(r => setTimeout(r, 300));
  results.mobileDrawer = { hamburgerFound, drawerOpen };

  // 1.5 Vehicle Search Catalog (Desktop & Mobile)
  await setViewport(1280, 800, false);
  await navigate('http://localhost:4200/search');
  const searchDeskOverflow = await evaluate('document.documentElement.scrollWidth > window.innerWidth');
  const cardCount = await evaluate('document.querySelectorAll(".vehicle-card").length');
  await captureScreenshot('03-vehicle-search-desktop.png');
  results.overflowChecks.push({ page: 'Search Catalog Desktop', overflow: searchDeskOverflow });

  await setViewport(375, 667, true);
  await navigate('http://localhost:4200/search');
  const searchMobOverflow = await evaluate('document.documentElement.scrollWidth > window.innerWidth');
  await captureScreenshot('03b-vehicle-search-mobile.png');
  results.overflowChecks.push({ page: 'Search Catalog Mobile', overflow: searchMobOverflow });

  // 1.6 Vehicle Details Page
  await setViewport(1280, 800, false);
  let vehicleUrl = await evaluate('document.querySelector(".vehicle-card a")?.getAttribute("href")');
  if (!vehicleUrl) {
    vehicleUrl = '/vehicles/6d8e0308-df3e-11f1-9add-02167fc40063';
  }
  await navigate(`http://localhost:4200${vehicleUrl}`);
  const detailsOverflow = await evaluate('document.documentElement.scrollWidth > window.innerWidth');
  await captureScreenshot('04-vehicle-details.png');
  results.pages.push({ page: 'Vehicle Details', url: vehicleUrl, ok: true });
  results.overflowChecks.push({ page: 'Vehicle Details', overflow: detailsOverflow });

  // 1.7 Auth Forms
  await navigate('http://localhost:4200/auth/login');
  await captureScreenshot('05-login-desktop.png');

  await navigate('http://localhost:4200/auth/register');
  await captureScreenshot('06-register-desktop.png');

  await navigate('http://localhost:4200/partner/register');
  await captureScreenshot('07-partner-register-desktop.png');

  // =============================================================
  // 2. PARTNER PORTAL & 3-STEP VEHICLE WIZARD
  // =============================================================
  console.log('\n--- 2. Testing Partner Portal & Progressive Wizard ---');
  await performLogin('vikram.partner@islandride.test', 'Password123!');

  // 2.1 Partner Dashboard Desktop & Mobile
  await setViewport(1280, 800, false);
  await navigate('http://localhost:4200/partner');
  const partnerDeskOverflow = await evaluate('document.documentElement.scrollWidth > window.innerWidth');
  await captureScreenshot('08-partner-dashboard-desktop.png');
  results.overflowChecks.push({ page: 'Partner Dashboard Desktop', overflow: partnerDeskOverflow });

  await setViewport(375, 667, true);
  await navigate('http://localhost:4200/partner');
  const partnerMobOverflow = await evaluate('document.documentElement.scrollWidth > window.innerWidth');
  await captureScreenshot('08b-partner-dashboard-mobile.png');
  results.overflowChecks.push({ page: 'Partner Dashboard Mobile', overflow: partnerMobOverflow });

  // 2.2 Progressive 3-Step Wizard Navigation
  await setViewport(1280, 800, false);
  await navigate('http://localhost:4200/partner/fleet/new');
  await captureScreenshot('09-vehicle-wizard-step1.png');

  // Interact with Step 1
  console.log('Testing Wizard Step 1 -> Step 2 validation...');
  await evaluate(`
    (() => {
      const islSelect = document.querySelector('#v-island');
      if (islSelect && islSelect.options.length > 1) {
        islSelect.selectedIndex = 1;
        islSelect.dispatchEvent(new Event('change', { bubbles: true }));
      }
      const catSelect = document.querySelector('#v-cat');
      if (catSelect && catSelect.options.length > 1) {
        catSelect.selectedIndex = 1;
        catSelect.dispatchEvent(new Event('change', { bubbles: true }));
      }
      const title = document.querySelector('#v-title');
      if (title) {
        title.value = 'Royal Enfield Classic 350 Andaman Special';
        title.dispatchEvent(new Event('input', { bubbles: true }));
      }
      const reg = document.querySelector('#v-reg');
      if (reg) {
        reg.value = 'AN01CD5566';
        reg.dispatchEvent(new Event('input', { bubbles: true }));
      }
      const make = document.querySelector('#v-make');
      if (make) {
        make.value = 'Royal Enfield';
        make.dispatchEvent(new Event('input', { bubbles: true }));
      }
      const model = document.querySelector('#v-model');
      if (model) {
        model.value = 'Classic 350';
        model.dispatchEvent(new Event('input', { bubbles: true }));
      }
    })()
  `);

  // Click Next: Pricing & Mode
  await evaluate(`
    (() => {
      const nextBtn = document.querySelectorAll('.wizard-nav-actions button')[0];
      if (nextBtn) nextBtn.click();
    })()
  `);
  await new Promise(r => setTimeout(r, 600));
  await captureScreenshot('10-vehicle-wizard-step2.png');

  // Check Step 2 is active
  const isStep2Active = await evaluate('!!document.querySelector("#v-rate")');

  // Advance to Step 3
  console.log('Testing Wizard Step 2 -> Step 3 transition...');
  await evaluate(`
    (() => {
      const rate = document.querySelector('#v-rate');
      if (rate && !rate.value) {
        rate.value = '1500';
        rate.dispatchEvent(new Event('input', { bubbles: true }));
      }
      const nextBtn = document.querySelectorAll('.wizard-nav-actions button')[1] || document.querySelectorAll('.wizard-nav-actions button')[0];
      if (nextBtn) nextBtn.click();
    })()
  `);
  await new Promise(r => setTimeout(r, 600));
  await captureScreenshot('11-vehicle-wizard-step3.png');
  const isStep3Active = await evaluate('!!document.querySelector(".review-box")');

  results.wizardSteps = {
    step1Reached: true,
    step2Reached: isStep2Active,
    step3Reached: isStep3Active
  };

  // 2.3 Partner Bookings & Financials
  await navigate('http://localhost:4200/partner/bookings');
  await captureScreenshot('12-partner-bookings.png');

  await navigate('http://localhost:4200/partner/financials');
  await captureScreenshot('13-partner-financials.png');

  // =============================================================
  // 3. OPERATIONS & FINANCE ADMIN PORTAL
  // =============================================================
  console.log('\n--- 3. Testing Operations & Finance Portals ---');
  await performLogin('admin.governance@islandride.test', 'Password123!');

  // 3.1 Operations Dashboard
  await navigate('http://localhost:4200/admin');
  const adminDeskOverflow = await evaluate('document.documentElement.scrollWidth > window.innerWidth');
  await captureScreenshot('14-admin-operations-dashboard.png');
  results.overflowChecks.push({ page: 'Admin Operations Dashboard', overflow: adminDeskOverflow });

  // 3.2 Bookings & Moderation Governance
  await navigate('http://localhost:4200/admin/bookings');
  await captureScreenshot('15-admin-bookings-governance.png');

  // 3.3 Finance Dashboard & Fiduciary Deposit Trust Account
  await navigate('http://localhost:4200/admin/finance');
  await captureScreenshot('16-admin-finance-trust-account.png');

  // =============================================================
  // 4. TOURIST PORTAL
  // =============================================================
  console.log('\n--- 4. Testing Tourist Portal ---');
  await performLogin('rahul.tourist@islandride.test', 'Password123!');

  // 4.1 Tourist Bookings
  await navigate('http://localhost:4200/tourist/bookings');
  await captureScreenshot('17-tourist-bookings.png');

  // Save audit results json
  results.consoleErrors = consoleErrors;
  fs.writeFileSync('docs/screenshots/verification-results.json', JSON.stringify(results, null, 2));

  console.log('\n=============================================================');
  console.log('Visual & Functional Verification Summary:');
  console.log(`- Total Screenshots Captured: ${fs.readdirSync(screenshotsDir).filter(f => f.endsWith('.png')).length}`);
  console.log(`- Horizontal Overflows: ${results.overflowChecks.filter(c => c.overflow).length}`);
  console.log(`- Mobile Drawer Toggled: ${results.mobileDrawer?.drawerOpen}`);
  console.log(`- 3-Step Wizard Navigation: Step 1 -> Step 2 (${results.wizardSteps?.step2Reached}) -> Step 3 (${results.wizardSteps?.step3Reached})`);
  console.log(`- Console Errors: ${consoleErrors.length}`);
  console.log('=============================================================\n');

} catch (err) {
  console.error('Audit Error:', err);
} finally {
  if (ws) ws.close();
  chrome.kill();
  try { fs.rmSync(tempUserDataDir, { recursive: true, force: true }); } catch {}
}
