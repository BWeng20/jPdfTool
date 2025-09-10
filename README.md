# jPdfTool
Apache PDFBox based tool to modify PDFs without paying any money.

This project began when I needed to protect a scanned PDF for an email, but the HP Scanner software couldn’t handle it.<br>
My old Brother scanner had this feature - plus other useful tools - and (by the way) worked without requiring any registration.<br>
Rather than paying for Adobe or other commercial solutions, I turned to the PDFBox documentation. <br>
I had used PDFBox in previous commercial projects, though I’d never dealt with PDF protection before.

Thirty minutes later, the first version was up and running, and I sent the email. 

The following five hours were spent adding fancy features to the UI and the build system - just for fun.


## Current Features

### 📄 Load a PDF

Easily import PDF documents for editing

### 👀 Preview the PDF (up to 2 pages)

View a limited preview of the document (up top 2 poages) using PDFBox's rendering engine.

### 🔐 Set Owner and User Passwords

You can set either an owner or a user password if specific permissions aren't required.<br>
Both options provide basic encryption and restrict viewing access.

### 🗜️ Enable/Disable Compression

Toggle compression to optimize file size.

### ✅ Set Permissions
Permissions only take effect when both owner and user passwords are set.<br>
The UI automatically disables permission checkboxes unless both passwords are provided.

### 🔓 Load Protected PDFs

Allows you to remove or change existing passwords.<br> 
If the current entered owner password does not match the original password of the loaded PDF, the UI will prompt you to 
enter the correct one.<br>
This password input is masked and will not be shown in the UI afterward.

### 💾 Store a Copy of the File
Saves the PDF with the updated settings applied.

## 🛠️ TODOs
1. Implement page removal and rearrangement functionality
2. Add support for merging multiple PDF files (verify PDFBox capabilities for that)
3. Enable modification of additional document properties


![Screenshot](doc/Screenshot%20Release1.0.png)