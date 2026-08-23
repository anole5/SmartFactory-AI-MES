// 构建产物可服务性检查（vite preview 冒烟）：根 HTML + 入口 JS 能取到
const htmlRes = await fetch('http://localhost:4173/')
const html = await htmlRes.text()
console.log('GET / status:', htmlRes.status)
console.log('has app div:', html.includes('id="app"'))
console.log('title:', (html.match(/<title>(.*?)<\/title>/) ?? [])[1])
const asset = (html.match(/src="(\/assets\/index-[^"]+\.js)"/) ?? [])[1]
if (asset) {
  const js = await fetch('http://localhost:4173' + asset)
  console.log(`GET ${asset} status:`, js.status)
} else {
  console.log('entry asset not found in html')
}
