// Shader'ları başsız Chromium'da WebGL ile çizer ve PNG kaydeder.
// Kullanım: node render_shader.js <ad> <genişlik> <yükseklik> <çıktı-öneki> <t1,t2,...> [dokunma x,y,yaş]
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const ASSETS = path.join(__dirname, '../wallpapers/app/src/main/assets');
const [name, W, H, outPrefix, timesArg, touchArg] = process.argv.slice(2);
const times = timesArg.split(',').map(Number);
const touch = touchArg ? touchArg.split(',').map(Number) : [-9999, -9999, 999];

const common = fs.readFileSync(path.join(ASSETS, 'shaders/common.glsl'), 'utf8');
const frag = common + '\n' + fs.readFileSync(path.join(ASSETS, `shaders/${name}.frag`), 'utf8');
const texFor = { galata: ['textures/galata.jpg', 'textures/galata_flow.png'] }[name] || [];
const texData = texFor.map(f => {
  const ext = f.endsWith('.png') ? 'png' : 'jpeg';
  return `data:image/${ext};base64,` + fs.readFileSync(path.join(ASSETS, f)).toString('base64');
});

const html = `<html><body style="margin:0;background:#000"><canvas id=c width=${W} height=${H}></canvas>
<script>
window.renderAt = async (fragSrc, texUrls, t, touch) => {
  const c = document.getElementById('c');
  const gl = c.getContext('webgl', {preserveDrawingBuffer: true}); gl.getExtension('OES_standard_derivatives');
  const sh = (type, src) => { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s)); return s; };
  const vs = 'attribute vec2 a_pos; varying vec2 v_uv; void main(){ v_uv = a_pos*0.5+0.5; gl_Position = vec4(a_pos,0.,1.); }';
  const p = gl.createProgram();
  gl.attachShader(p, sh(gl.VERTEX_SHADER, vs)); gl.attachShader(p, sh(gl.FRAGMENT_SHADER, fragSrc));
  gl.linkProgram(p); if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
  gl.useProgram(p);
  const b = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, b);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1,1,-1,-1,1,1,1]), gl.STATIC_DRAW);
  const loc = gl.getAttribLocation(p, 'a_pos'); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
  let size = [1,1];
  for (let i = 0; i < texUrls.length; i++) {
    const img = new Image(); img.src = texUrls[i]; await img.decode();
    if (i === 0) size = [img.width, img.height];
    const tx = gl.createTexture(); gl.activeTexture(gl.TEXTURE0 + i); gl.bindTexture(gl.TEXTURE_2D, tx);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGB, gl.RGB, gl.UNSIGNED_BYTE, img);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.uniform1i(gl.getUniformLocation(p, 'u_tex' + i), i);
  }
  gl.uniform2f(gl.getUniformLocation(p, 'u_res'), c.width, c.height);
  gl.uniform1f(gl.getUniformLocation(p, 'u_time'), t);
  gl.uniform3f(gl.getUniformLocation(p, 'u_touch'), touch[0], touch[1], touch[2]);
  gl.uniform1f(gl.getUniformLocation(p, 'u_offset'), 0.5);
  gl.uniform2f(gl.getUniformLocation(p, 'u_tex0Size'), size[0], size[1]);
  gl.viewport(0, 0, c.width, c.height);
  const t0 = performance.now();
  gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4); gl.finish();
  return performance.now() - t0;
};
</script></body></html>`;

(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: +W, height: +H }, deviceScaleFactor: 1 });
  await page.setContent(html);
  for (const t of times) {
    const ms = await page.evaluate(([f, tx, t, touch]) => window.renderAt(f, tx, t, touch), [frag, texData, t, touch]);
    const out = `${outPrefix}_${t}.png`;
    await page.locator('#c').screenshot({ path: out });
    console.log(`${out} (${ms.toFixed(0)} ms, yazılım GPU)`);
  }
  await browser.close();
})().catch(e => { console.error('HATA:', e.message); process.exit(1); });
