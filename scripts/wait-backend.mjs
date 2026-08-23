// 轮询后端就绪（登录接口返回 200 即认为启动完成）
const res = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' }),
});
if (res.status === 200) {
  console.log('UP');
  process.exit(0);
}
console.log('NOT READY, status=' + res.status);
process.exit(1);
