<template>
  <main class="not-found">
    <span>404</span>
    <h1>页面没有找到</h1>
    <p>你访问的地址不存在或已经移动。</p>
    <router-link to="/dashboard">返回工作台</router-link>
  </main>
</template>

<style scoped>
.not-found{display:flex;min-height:100vh;flex-direction:column;align-items:center;justify-content:center;background:#f5f7fb}.not-found span{color:#dfe5f8;font-size:120px;font-weight:700;line-height:1}.not-found h1{margin:20px 0 7px;color:#354259;font-size:21px}.not-found p{margin:0 0 20px;color:#8c96a7;font-size:11px}.not-found a{padding:10px 17px;border-radius:7px;color:#fff;background:#3156d3;font-size:10px}
</style>
