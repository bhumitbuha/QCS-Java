Quantum Circuit Simulator – Assignment 6 (Client/Server with Database)
Author: Bhumit Buha

Overview
This project extends the Quantum Circuit Simulator with:

Login and Sign-Up using SQLite database.

Client/Server communication over TCP sockets.

Save and Load circuits to/from a database.

Multi-client server with Swing GUI.

JavaFX client with full MVC, language switching, theme change, and file I/O.

Included Files
Server: qcs.QCSServer (Swing GUI).

Client: qcs.QCSLoginClient → launches main MVC (qcs.QCSMain, qcs.QCSController).

Protocol: qcs.QCSProtocol.

Properties: QCSMessages_en.properties, QCSMessages_fr.properties.

Images: QCClient.png, QCServer.png, qcs.png, qcsicon.jpg (in images\ folder).

SQLite JDBC driver in lib\ folder (e.g., sqlite-jdbc.jar).

Batch Script: A4_run.bat (builds, packages, generates Javadoc, and runs).

Folder Structure
QCS\
├─ src\qcs\ (Java source and properties)
├─ images\ (all required images)
├─ lib\ (sqlite-jdbc.jar)
├─ bin\ (compiled output)
├─ qcs_doc\ (generated Javadoc)
├─ qcs.db (SQLite database)
├─ A4_run.bat
└─ README.txt

How to Build and Run
Place sqlite-jdbc.jar in the lib\ folder.

Open Command Prompt in the QCS folder.

Run:
A4_run.bat

The script will:

Compile all Java files to bin\.

Copy properties and images to bin\.

Package into QCS.jar.

Generate Javadoc in qcs_doc\.

Launch the application.

Server Usage
Create DB: Drops and recreates User and QuantumCircuits tables.

Start: Starts the server on the given port (default 12345).

Show DB: Displays all users and circuits in the log.

Finalize: Placeholder for client cleanup.

End: Shuts down the server.

Client Usage
Launch the client from the login screen.

Enter username, server (localhost), and port (12345).

Click Connect.

Sign up (first time) or log in.

In the main window:

Design the circuit using the left-side gate palette.

Use Send Circuit to save to the server.

Use Load Circuit to retrieve from the server.

Use file menu for local save/load.

Switch language, theme, or mode from the menu bar.

Protocol Reference
Separator: #

Login: username#P0#password → username#P0#OK or FAIL

Signup: username#P1#password → username#P1#OK or FAIL

Save: username#P2#<circuit> → username#P2#OK or FAIL

Load: username#P3 → username#P3#<circuit> or username#P3#FAIL

Notes
Ensure all images are in the images\ folder.

Ensure properties files are in src\qcs\.

SQLite database file (qcs.db) will be created in project root when first run.