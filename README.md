# Quantum Circuit Simulator (QCS)

A Java desktop application for designing, visualising, and stepping through quantum circuits. Built as a client–server system: a Swing-based server manages user accounts and saved circuits via SQLite, while a JavaFX client provides the interactive circuit editor.

---

## Features

- **Visual circuit editor** — place gates on a qubit grid by clicking cells
- **Gate palette** — single-qubit gates (I, X, Y, Z, H, S, T, U), multi-qubit gates (CX, SWAP, CU, CCX), and BARRIER
- **Step simulation** — advance column by column; each step logs the tensor product of active gates
- **Save / Load circuits** — to local `.qcs` files or to the server under your account
- **User authentication** — Sign Up and Login via the server (SHA-256 hashed passwords)
- **Themes** — Light, Dark, Sky, Grayscale
- **Gate colour customisation** — change any gate's display colour
- **Grid resize** — adjust qubit count and circuit depth at any time
- **Language support** — English and French UI (via resource bundles)
- **Qiskit export** — each simulation step prints equivalent Qiskit API calls to the message area

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | **26** (OpenJDK or compatible) | [Download OpenJDK 26](https://jdk.java.net/26/) |
| JavaFX SDK | **26**, Windows x64 | [Download from Gluon](https://gluonhq.com/products/javafx/) — choose SDK, not jmods |
| SQLite JDBC | any | Already included in `lib/sqlite-jdbc.jar` |

> **JavaFX must be downloaded separately** — it is not bundled in the repo due to its size (~115 MB). See setup below.

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/bhumitbuha/QCS-Java.git
cd QCS-Java
```

### 2. Download JavaFX 26 SDK

1. Go to **https://gluonhq.com/products/javafx/**
2. Select: Version **26**, Operating System **Windows**, Architecture **x64**, Type **SDK**
3. Download and extract the zip
4. Place the extracted folder in the project root so the path is:

```
QCS-Java/
  javafx-sdk-26.0.1/       ← must be here
    bin/                   ← contains prism_d3d.dll, glass.dll, etc.
    lib/                   ← contains javafx.controls.jar, etc.
  src/
  lib/
  A6_Run.bat
  ...
```

> If your extracted folder has a different version number (e.g. `javafx-sdk-26.0.2`), update the `JAVA_FX_LIB` line in `A6_Run.bat` to match.

---

## Running the Application

### Option A — Batch Script (easiest)

Open **Command Prompt** (not PowerShell) in the project root:

```cmd
A6_Run.bat
```

This will:
1. Compile all source files with JDK 26
2. Package `qcsclient.jar` and `qcsserver.jar` into `bin/`
3. Generate Javadoc into `qcs_doc/`
4. Launch **QCS Server** and **QCS Client** as separate windows

**First-time run:**
- In the **Server** window → click **Create DB**, then click **Start**
- In the **Client** window → fill in *User* + *Server* (`localhost`) + *Port* (`12345`) → click **Connect** → enter a password → click **Sign Up** → then **Login**

### Option B — IntelliJ IDEA

1. **Open** the project (`File → Open → select the QCS-Java folder`)

2. **Set the SDK** (`File → Project Structure → Platform Settings → SDKs`):
   - Click **+** → **Add JDK** → navigate to your JDK 26 installation
   - Name it `openjdk-26` (must match the name in `QCS.iml`)

3. **Run QCSServer first** — use the `QCSServer` run configuration (already saved in the project). No VM options needed.

4. **Run QCSLoginClient** — use the `QCSLoginClient` run configuration. It requires these **VM options** (set once via *Edit Configurations → VM options*):

```
--module-path "C:\path\to\QCS-Java\javafx-sdk-26.0.1\lib"
--add-modules javafx.controls,javafx.fxml,javafx.graphics
--enable-native-access=javafx.graphics
```

Replace `C:\path\to\QCS-Java` with your actual project root path.

Also set:
- **Working directory**: project root (e.g. `C:\Users\YourName\IdeaProjects\QCS-Java`)
- **JRE**: your JDK 26 installation

> **Tip:** Once QCSServer is running you can also click the **Launch Client** button in the server window to open the login screen without separately launching QCSLoginClient.

---

## Project Structure

```
QCS-Java/
├── src/qcs/
│   ├── QCSLoginClient.java   JavaFX login & signup screen
│   ├── QCSMain.java          JavaFX Application entry point (splash → editor)
│   ├── QCSController.java    Main UI controller (gate palette, grid, menus, networking)
│   ├── QCSModel.java         Circuit data model (grid state, gate colours, file I/O)
│   ├── QCSPanel.java         JavaFX GridPane of circuit cells
│   ├── QCSGridButton.java    Individual circuit cell button
│   ├── QCSSplash.java        3-second splash screen
│   ├── QCSServer.java        Multi-threaded Swing server with SQLite storage
│   ├── QCSProtocol.java      Network protocol constants (P0–P9)
│   └── QCSModelException.java Custom exception for gate placement errors
├── lib/
│   └── sqlite-jdbc.jar       SQLite JDBC driver (bundled)
├── images/
│   ├── qcs.png               Banner shown in the circuit editor
│   ├── QCClient.png          Banner shown in the login client
│   ├── QCServer.png          Banner shown in the server window
│   └── qcsicon.jpg           App icon / splash logo
├── javafx-sdk-26.0.1/        JavaFX SDK — download separately (not in repo)
├── A6_Run.bat                Build + launch script
├── QCS.iml                   IntelliJ module file
└── README.md
```

---

## Network Protocol

All messages are single UTF-8 text lines over TCP, fields separated by `#`.

| Code | Client → Server | Server → Client |
|---|---|---|
| `P0` | `username#P0#password` — Login | `username#P0#OK` or `username#P0#FAIL` |
| `P1` | `username#P1#password` — Sign Up | `username#P1#OK` or `username#P1#FAIL` |
| `P2` | `username#P2#<circuit>` — Save circuit | `username#P2#OK` or `username#P2#FAIL` |
| `P3` | `username#P3` — Load circuit | `username#P3#<circuit>` or `username#P3#FAIL` |

**Circuit serialisation format:** rows separated by `;`, columns by `,`, empty cells as `_`.  
Multi-qubit gates use numbered suffixes: `CX(1)`, `CX(2)`, `CCX(1)`, `CCX(2)`, `CCX(3)`.  
Save files (`.qcs`) store `step=N` on line 1 and the serialised grid on line 2.

---

## Database Schema

```sql
CREATE TABLE User (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL   -- SHA-256 hex digest
);

CREATE TABLE QuantumCircuits (
    username TEXT PRIMARY KEY,
    circuit  TEXT
);
```

---

## Author

**Bhumit Buha** — Student ID 04109070  
Java Application Programming — Summer 2025
