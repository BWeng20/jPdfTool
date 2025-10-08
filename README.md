# jPdfTool
Apache PDFBox based tool to modify PDFs without paying any money.

This project began when I needed to protect a scanned PDF for an email, but the HP Scanner software couldn’t handle it.<br>
My old Brother scanner had this feature - plus other useful tools - and (by the way) worked without requiring any registration.<br>
Rather than paying for Adobe or other commercial solutions, I turned to the PDFBox documentation. <br>
I had used PDFBox in previous commercial projects, though I’d never dealt with PDF protection before.

Thirty minutes later, the first version was up and running, and I sent the email. 

The following days were spent adding fancy features to the UI and the build system - just for fun.


## Current Features

### 📄 Load a PDF

Easily import PDF documents for editing

### 📚 Merge Multiple PDFs

Add additional PDFs and either mix pages or append them sequentially.

### 🔐 Set Owner and User Passwords

You can set either an owner or a user password if specific permissions aren't required.<br>
Both options provide basic encryption and restrict viewing access.<p>
If you need dedicated permission, you have to set both passwords. 

### 🗜️ Enable/Disable Compression

Toggle compression to optimize file size.

### ✅ Set Permissions

Permissions only take effect when both owner and user passwords are set.<br>
The UI automatically disables permission checkboxes unless both passwords are provided.

### 🧹 Remove & Rearrange Pages

Delete pages or change their order.

### 🔄 Rotate Pages

Rotate pages by 90°, 180° or 270°.

### 👀 Preview PDF

For you convenience the document is shown using PDFBox’s rendering engine 
(rending runs in background).

### 🖼️ Export Embedded Images

Display and export embedded images.<br>
Some PDF tools seems to split images into fragments - this tool can provide a merged version for you.

### 🔓 Load Protected PDFs

Allows you to remove or change existing passwords.<br> 
If the current entered owner password does not match the original password of the loaded PDF, the UI will prompt you to 
enter the correct one.<br>
This password input is masked and will not be shown in the UI afterward.

### 💾 Store the File

Saves your modified PDF.

### ✂️ Split into Multiple Documents

Export the PDF into separate documents.

### 🎨 Switch "Look-And-Feels"

Toggle between FlatLaf Dark/Light themes or system defaults.<br>
The setting is stored in user-preferences.

## 🛠️ TODOs

E-mail me if I missed important features. 

![Screenshot](doc/Screenshot%20Release1.3.png)