// Packs PNG files into one .ico (Windows Vista+ and every browser read PNG-in-ICO).
// Usage: node make-ico.js out.ico in-16.png in-32.png ...
const fs = require('fs');
const [out, ...inputs] = process.argv.slice(2);
if (!out || inputs.length === 0) { console.error('usage: node make-ico.js out.ico in.png...'); process.exit(1); }

const images = inputs.map((path) => {
  const data = fs.readFileSync(path);
  if (data.readUInt32BE(0) !== 0x89504e47) throw new Error(`${path} is not a PNG`);
  return { data, width: data.readUInt32BE(16), height: data.readUInt32BE(20) };
});

const header = Buffer.alloc(6);
header.writeUInt16LE(0, 0);              // reserved
header.writeUInt16LE(1, 2);              // type 1 = icon
header.writeUInt16LE(images.length, 4);

let offset = 6 + 16 * images.length;
const entries = images.map(({ data, width, height }) => {
  const e = Buffer.alloc(16);
  e.writeUInt8(width >= 256 ? 0 : width, 0);   // 0 means 256
  e.writeUInt8(height >= 256 ? 0 : height, 1);
  e.writeUInt8(0, 2);                          // no palette
  e.writeUInt8(0, 3);                          // reserved
  e.writeUInt16LE(1, 4);                       // colour planes
  e.writeUInt16LE(32, 6);                      // bits per pixel
  e.writeUInt32LE(data.length, 8);
  e.writeUInt32LE(offset, 12);
  offset += data.length;
  return e;
});

fs.writeFileSync(out, Buffer.concat([header, ...entries, ...images.map((i) => i.data)]));
console.log(`wrote ${out} (${images.map((i) => i.width).join(', ')} px)`);
