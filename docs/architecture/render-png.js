import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';

const svgPath = path.resolve('docs/architecture/fotofetch-overall-architecture.svg');
const htmlWrapperPath = path.resolve('docs/architecture/render-wrapper.html');
const pngPath = path.resolve('docs/architecture/fotofetch-overall-architecture.png');

const svgData = fs.readFileSync(svgPath, 'utf8');

const html = `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    * { box-sizing: border-box; }
    html, body {
      margin: 0;
      padding: 0;
      width: 2600px;
      height: 1860px;
      overflow: hidden;
      background-color: #0F172A;
    }
    svg {
      width: 2600px;
      height: 1860px;
      display: block;
    }
  </style>
</head>
<body>
  ${svgData}
</body>
</html>`;

fs.writeFileSync(htmlWrapperPath, html, 'utf8');

const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const cmd = `"${edgePath}" --headless --disable-gpu --screenshot="${pngPath}" --window-size=2600,1860 "${htmlWrapperPath}"`;

console.log('Rendering high-resolution PNG with Microsoft Edge...');
execSync(cmd);

if (fs.existsSync(pngPath)) {
  const stats = fs.statSync(pngPath);
  console.log(`Successfully generated high-resolution PNG at: ${pngPath} (${(stats.size / 1024).toFixed(1)} KB)`);
  // Clean up temporary HTML wrapper
  try { fs.unlinkSync(htmlWrapperPath); } catch (e) {}
} else {
  console.error('PNG was not generated!');
  process.exit(1);
}
