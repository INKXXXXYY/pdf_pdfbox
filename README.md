## PDFBox 双击行级编辑 Demo（PDF.js + Spring Boot + PDFBox）

### 功能
- 预览 PDF（PDF.js 渲染）
- 后端绘制红框（行级），可对齐渲染结果
- 双击行级红框就地编辑：预填充整行原文，回车替换后重新渲染
- 编辑原生文字（三种策略，作对比）：
  - 全文重排（/api/pdf/edit）
  - 原位替换保版式（/api/pdf/edit-inplace）
  - 整行替换（/api/pdf/edit-line）
- 下载修改后的 PDF

### 技术栈
- 前端：原生 HTML + Vue 3 CDN + PDF.js CDN + fetch（不再依赖 axios）
- 后端：Spring Boot 3 + Apache PDFBox 2.0.x

### 目录结构
```
project-root/
├─ frontend/
│  └─ index.html                 # Vue + PDF.js 一页式 Demo，预览/编辑/下载
├─ server/
│  ├─ data/
│  │  ├─ example.pdf             # 启动时自动生成的示例 PDF
│  │  └─ modified (7).pdf        # 你在本地测试生成的样例（可忽略）
│  ├─ pom.xml                    # Spring Boot + PDFBox 依赖与构建
│  ├─ src/main/java/com/example/pdfdemo/
│  │  ├─ PdfboxApplication.java  # 启动类
│  │  ├─ config/CorsConfig.java  # 全局 CORS 放开
│  │  ├─ controller/PdfController.java  # REST 接口：/sample、/edit、/edit-inplace、/edit-line
│  │  └─ service/
│  │     ├─ PdfService.java       # PDF 读写、重排、原位替换、整行替换核心逻辑
│  │     ├─ TextSearcher.java     # 文本定位：字符匹配、行信息采集
│  │     └─ TextBoxCollector.java # 行/词包围框收集（坐标与文本）
│  └─ src/main/resources/application.yml # 端口等配置（默认 8080）
└─ README.md
```

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
  - 简单示例：抽取全文为字符串→替换→单页重排写回
- POST `/api/pdf/edit-inplace`：Body: `{ oldText, newText, ignoreCase }`
  - 原位替换（尽量保留版式）：定位旧词坐标，白底遮盖，再用原字体/字号在同一基线写入新词
- POST `/api/pdf/edit-line`：Body: `{ oldText, newText, ignoreCase }`
  - 整行替换：找到包含旧词的“行”，整行覆盖后按字符串替换后的内容重新绘制，避免长词遮挡后续文字
- GET `/api/pdf/annotated?mode=line|word`：返回带红框标注的 PDF（`mode` 控制行/词级）
- GET `/api/pdf/text-boxes?mode=line|word`：返回 JSON 文本框数组（坐标单位为 PDF 用户空间点，原点左下）

返回的 Box 结构示例（行/词通用）：

```
[
  {
    "pageIndex": 0,
    "x": 72.0,
    "yTop": 700.5,
    "width": 320.0,
    "height": 16.4,
    "text": "This is a line text",
    "pageWidth": 595.0,
    "pageHeight": 842.0
  }
]
```

### 说明与限制
- 全文重排（/edit）用于“流程演示”，不保留原始版式/分页/字体嵌入。
- 原位替换（/edit-inplace）与整行替换（/edit-line）尽量保留版式，但属于“增量绘制”，不会修改原内容流：
  - 旧内容通过“白底矩形”遮盖，新内容追加在页面内容流末尾（AppendMode.APPEND）。
  - 字体选择优先使用原 `TextPosition.getFont()` 与 `getFontSizeInPt()`，若缺失回退到 `PDType1Font.HELVETICA`。
  - 坐标使用文字矩阵的平移（`TextPosition.getTextMatrix().getTranslateX/Y()`）作为基线坐标，减少渲染器差异带来的偏移。

### 实现思路（简述）
- 文本定位（`TextSearcher`）
  - 继承 `PDFTextStripper`，在 `writeString` 中获取每个内容块的 `List<TextPosition>`。
  - 进行子串匹配，记录：页号、起点/末端坐标、宽高、字体/字号、基线（tx, ty）等信息。
  - 同时抽取“行信息”（粗粒度）：将同一内容块视作一行，记录起点/基线/宽高/字体/字号与整行文本。

- 原位替换（/edit-inplace）
  - 参照匹配到的字符范围，基于字体上升/下降（ascent/descent）和原宽度估算覆盖矩形。
  - 先 `fill` 白底，再在同一基线 `showText(newText)`。
  - 为避免遮住后续文字，覆盖矩形只覆盖“旧词区域”；若新词更长，当前实现提供两种策略：
    - A：缩小字号适配旧词宽度（不挪动后文）
    - B：保持字号并右移同块后续文本（已实现简版，能处理在同一内容块内的中文/英文常见场景）

- 整行替换（/edit-line）
  - 先找到“包含命中词”的行（同页且基线接近），覆盖整行区域；
  - 将行文本做字符串替换后按原字体/字号在原起点和基线重绘，避免长词导致遮挡。

- 行级识别与交互（新增）
  - `TextBoxCollector` 行聚合：同页且 `|baselineY差| ≤ 1pt`；
  - 行框：`x = min(XDirAdj)`，`width = max(XDirAdj+WidthDirAdj) - x`，`yTop = baselineY + max(ascent)`，`height = (ascent + descent)`；
  - 前端加载 `/annotated?mode=line` 作为底图，同时拉取 `/text-boxes?mode=line` 叠加透明 span；
  - 双击 span → 输入框预填充 `data-line-text` → 调用 `/edit-line` → 用返回 PDF 重新渲染。

### 实现步骤（后端识别 + 前端双击编辑）

1) 后端识别与标注
- 使用 `TextBoxCollector`（行级：`Mode.LINE`；词级：`Mode.WORD`）遍历每页 `writeString` 的 `TextPosition`，计算：
  - 行：按同页且基线 `baselineY` 差值 ≤ 1pt 聚合，取最小左 x、最大右 x、最大上升距 ascent、最大下降距 descent，得到包围框：
    - `x = minX`
    - `yTop = baselineY + maxAscent`
    - `width = maxX - minX`
    - `height = maxAscent + maxDescent`
  - 词：按相邻字符间距阈值与空白切分，逐词输出包围框与 `text`。
- 暴露接口：
  - `GET /api/pdf/text-boxes?mode=line|word` → 返回 Box 数组供前端交互层使用。
  - `GET /api/pdf/annotated?mode=line|word` → 在原 PDF 上追加红色虚线框并返回，用作预览底图。

2) 前端覆盖层与双击编辑
- 用 PDF.js 渲染底图（可直接使用 `/annotated` 的流）。
- 请求 `/text-boxes`（默认行级），仅取当前页的 Box，按同一缩放比 `scale` 将坐标映射为像素：
  - `leftPx = x * scale`
  - `topPx = (pageHeight - yTop) * scale`（PDF 原点左下，CSS 原点左上，需翻转 y）
  - `widthPx = width * scale`，`heightPx = height * scale`
- 在画布上方放置绝对定位的 `span.textItem`，保存属性：`data-line-text`、`data-page-index`。
- 绑定 `ondblclick`：在目标 `span` 上方显示输入框，预填充整行文本；回车或失焦提交：
  - POST `/api/pdf/edit-line`，Body：`{ oldText: lineText, newText, pageIndex, ignoreCase }`
  - 成功后以返回的 PDF Blob 作为新的渲染源，重新调用渲染函数。

3) 重要约定与对齐
- 前后端必须使用一致的缩放比计算覆盖层像素位置；前端应保存 `pageWidth/pageHeight` 以便坐标换算。
- 当前示例仅渲染第 1 页并过滤 `pageIndex === 0`；多页时需：
  - 循环 `pdf.getPage(n)` 渲染多个 `canvas`；
  - 为每页创建独立的 `textLayer` 容器并按对应 `pageIndex` 叠加 Box。

### 已知坑与规避建议
- 字体与字形
  - 原文可能使用嵌入字体或子集字体，替换文本包含未嵌入字符会触发 PDF 渲染回退，导致字形不一致。
  - 建议：
    - 为中文/英文字体准备后备字体，检测 `font.hasGlyph(codePoint)` 不支持时切换；
    - 或将目标字体嵌入文档（PDFBox 嵌入 TTF 需要引入 `PDType0Font` 并遵守字体许可）。

- 坐标与基线
  - 不同 PDF 创建工具会导致 `getXDirAdj/YDirAdj` 与文字矩阵基线不一致，容易出现上下错位。
  - 采用文字矩阵 `translateX/Y` 更稳妥，但极端情况下仍可能有微偏差；可通过小量偏移或逐字定位微调。

- 内容块切分
  - PDF 文本经常被切成多个内容块（换行、连字、字距/字距调整、不同字体），跨块直接匹配会漏。
  - 本项目在行级采取“同块视为一行”的近似策略；若出现跨块拆分严重，可：
    - 逐字聚类合并为逻辑行；
    - 逐字覆盖与重绘，按每个 `TextPosition` 的宽度与间距精确排布。

- 遮盖矩形“露边”
  - 用 ascent/descent 推算高度、用字符串宽度估算宽度，但不同渲染器可能仍有露边。
  - 建议对覆盖矩形加轻微内边距（pad），并优先以末字符右端坐标估算宽度。

- 性能与内存
  - 大 PDF 或大量替换建议批量处理并写入临时文件流；必要时分页处理，避免一次性装载过大内容。

### 适用场景
- 需要在后端（Java）环境里对 PDF 做简单文字替换，且优先保持版式的企业部署场景。
- 前端只做渲染与发起编辑指令（JSON），不上传整文档（可扩展为上传文件接口）。

### 可能的扩展

### 封版与发布

- 当前封版：行级识别 + 后端红框绘制 + 双击行级编辑；
- 建议打标签：`v1.0.0`；
- 提交到 GitHub：
```
git init
git add -A
git commit -m "feat: 行级红框与双击编辑; docs: README"
git branch -M main
git remote add origin <YOUR_GITHUB_REPO_URL>
git push -u origin main
git tag -a v1.0.0 -m "v1.0.0 行级编辑"
git push origin --tags
```
- 上传任意 PDF：新增上传接口，临时保存为会话文件并在编辑时指定目标文件。
- 更强的“原位替换”：
  - 逐字重绘、按字间距/字距/旋转角度精确放置；
  - 处理跨内容块与连字（ligature）；
  - 保留/还原原内容流（需要解析并重写内容流指令）。
- 字体管理：支持外部 TTF 注册与嵌入，完善中英文混排与回退链。


