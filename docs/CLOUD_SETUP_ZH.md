# 云同步一次性设置（开发者做一次，用户什么都不用配）

做完下面三步之后，安装包里就自带登录信息。用户那边只需要：

- **Google Drive**：点「连接 Google Drive」→ 设同步密码 → 选 Google 账号 → 点「允许」。
- **GitHub**：点「连接 GitHub」→ 设同步密码 → 浏览器自动打开，粘贴代码（已自动复制）→ 点「Authorize」。
  软件会自己在用户的 GitHub 账号里建一个私有仓库 `life-assistant-data`。

手机和电脑用**同一个账号**、**同一个密码**（Windows 上就是数据密码）就会自动同步。

---

## 第一步：GitHub（约 3 分钟）

> 注意：这里要建的是 **OAuth App**，不是 GitHub App。

1. 打开 <https://github.com/settings/applications/new>（登录你的 ced2711 账号）。
2. 按下面填：

   | 栏目 | 填什么 |
   | --- | --- |
   | Application name | `Life Assistant` |
   | Homepage URL | `https://github.com/ced2711/life-assistant` |
   | Application description | 可以不填 |
   | Authorization callback URL | `https://github.com/ced2711/life-assistant`（用不到，但必须填） |
   | **Enable Device Flow** | **一定要打勾** |

3. 点 **Register application**。
4. 页面上会显示 **Client ID**（以 `Ov23` 开头）。把它复制下来，第三步要用。
   **不要**点 “Generate a new client secret”，用不到。

## 第二步：Google（约 10 分钟）

> **目前暂停**：Google 要求先在 Branding 页面补全资料（应用首页、隐私政策链接，以及经过验证的域名）才能正式发布。
> 在那之前，软件里的 Google Drive 按钮显示「即将推出」，只提供 GitHub 同步。下面的步骤留着以后用。

如果以前已经为这个软件建过 Google Cloud 项目，就用那个旧项目，别再新建（换项目会看不到旧的云端备份）。
下面的链接都会打开**当前选中的项目**，所以每一步都先看一眼左上角的项目名对不对。

1. **建项目**：打开 <https://console.cloud.google.com/projectcreate>，项目名填 `Life Assistant`，点「创建」。
   创建完在左上角把它选上。
2. **开启 Drive API**：打开 <https://console.cloud.google.com/apis/library/drive.googleapis.com>，点「启用 / Enable」。
3. **应用信息**：打开 <https://console.cloud.google.com/auth/overview>，点「开始 / Get started」：
   - App name：`Life Assistant`
   - User support email：选你的 Gmail
   - Audience（受众）：选 **External（外部）**
   - Contact information：填你的 Gmail
   - 勾选同意政策，点「创建 / Create」。
   - **不要上传 Logo**。上传 Logo 会触发 Google 审核，没有 Logo 就不用审核。
4. **权限范围**：打开 <https://console.cloud.google.com/auth/scopes>，点「添加或移除范围 / Add or remove scopes」。
   在最下面「手动添加范围 / Manually add scopes」里粘贴：

   ```
   https://www.googleapis.com/auth/drive.appdata
   ```

   点「添加到表格 / Add to table」→「更新 / Update」→ 页面底部「保存 / Save」。
   这个范围只能访问软件自己的隐藏文件夹，看不到用户的其他文件，所以 Google 不要求审核。
5. **正式发布**：打开 <https://console.cloud.google.com/auth/audience>，点「发布应用 / Publish app」→「确认」。
   状态要显示 **In production（正式版）**。
   不发布的话，只有你手动添加的测试账号能登录，而且每 7 天就要重新登录一次。
6. **Android 客户端**：打开 <https://console.cloud.google.com/auth/clients>，点「创建客户端 / Create client」：
   - Application type：**Android**
   - Name：`Life Assistant Android`
   - Package name：`com.ced2711.lifetracker`
   - SHA-1 certificate fingerprint：

     ```
     9E:E4:33:4E:D0:F6:D2:C5:31:39:66:44:30:19:7D:83:79:66:C1:18
     ```

     （这是正式版签名证书的 SHA-1，来自 `life-assistant-signing\certificate-fingerprints.txt`。）
   - 点「创建 / Create」。这一步什么都不用复制。
7. **Windows 客户端**：还是在 <https://console.cloud.google.com/auth/clients>，再点一次「创建客户端」：
   - Application type：**Desktop app（桌面应用）**
   - Name：`Life Assistant Windows`
   - 点「创建」。
   - 弹出的窗口里**马上点「下载 JSON / Download JSON」**。密钥只在这时显示一次，关掉就再也看不到了。
   - 把下载的文件放到 仓库文件夹旁边的 `life-assistant-signing\` 文件夹（**不要**放进代码仓库）。

## 第三步：告诉 Claude

把第一步的 GitHub Client ID 发过来，再说一声 Google 的 JSON 已经放进 `life-assistant-signing` 文件夹。
剩下的交给 Claude：把它们写进个人 Gradle 配置（不进 git），重新打包 APK 和 Windows 安装包，再实际连一次试试。

---

## 常见问题

| 看到的提示 | 原因 / 解决 |
| --- | --- |
| Google：「Developer error」或「代码 10」 | Android 客户端的包名或 SHA-1 填错了；也可能装的是 debug 版本（只有正式版签名才能登录）。 |
| Google：「Access blocked」/「此应用未经验证」 | 第 5 步没点发布，或者上传了 Logo。 |
| Google：另一台设备上看不到云端数据 | 两台设备登录的 Google 账号不同，或者 Android 和 Windows 客户端不在同一个项目里。 |
| GitHub：「Device flow is off」 | 第一步没勾 Enable Device Flow。去 <https://github.com/settings/developers> 打开这个应用补上。 |
| 两边都显示「密码不对」 | 两台设备的同步密码不一样。Windows 上的同步密码就是打开软件时输入的数据密码。 |
