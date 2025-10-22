## PDF 预览与编辑 Demo（PDF.js + Spring Boot + PDFBox）

### 功能
- 预览 PDF（PDF.js 渲染）
- 编辑原生文字（基于 PDFBox：文本抽取后整体重排渲染，示例级）
- 下载修改后的 PDF

### 技术栈
- 前端：原生 HTML + Vue 3 CDN + PDF.js CDN + Axios CDN
- 后端：Spring Boot 3 + Apache PDFBox 2.0.x

### 运行后端
1. 进入 `server` 目录
2. 运行：`mvn spring-boot:run`
   - 首次会下载依赖，请保持联网
3. 启动成功后访问健康接口：`http://localhost:8080/api/pdf/sample`

### 运行前端
方式 A（直接打开文件）：
1. 打开 `frontend/index.html`
2. 若浏览器限制跨源 file://，建议使用方式 B

方式 B（本地静态服务）：
1. 在 `frontend` 目录运行 `python -m http.server 5173` 或 `npx serve -p 5173`
2. 打开浏览器访问 `http://localhost:5173`

### API
- GET `/api/pdf/sample`：返回示例 PDF（二进制）
- POST `/api/pdf/edit`：Body: `{ oldText, newText }`，返回修改后的 PDF（二进制）

### 说明与限制
- 本示例采用“抽取全文 → 字符串替换 → 重新排版写入单页”的简单策略，适合演示流程，不保留原版式、字体与排版。
- 若需原位精确替换（保版式），需做文字定位（TextPosition）与内容流重写，复杂度较高，建议另行实现。


