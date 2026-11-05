# ResolveStudio — Landing Page (Vercel Dedicated Web Root)

This is the dedicated web root folder for deploying the ResolveStudio landing page on Vercel.

## Vercel Project Settings:
- **Framework Preset**: `Other`
- **Root Directory**: `landing`
- **Build Command**: `npm run build` (or leave default, `vercel.json` manages it)
- **Output Directory**: `dist` (or leave default, `vercel.json` configures it)

## Assets & Structure:
- `index.html`: Main landing page built with MotionSites craft
- `vercel.json`: Pre-configured rewrites, cache headers, and build commands
- `package.json`: Build script that bundles assets to `dist/`
- `screenshots/`: 5 high-resolution calibrated Android app screenshots
