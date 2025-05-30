# Silk Loader

**Silk Loader is a [Fabric](https://github.com/FabricMC/fabric-loader) GameProvider
that enables Fabric modding for the game [Equilinox](https://www.equilinox.com/).**

## Features

* **Enables Fabric Modding:** Load and run Fabric mods in Equilinox.
* **Compatibility Layer:** Provides necessary hooks and patches for Fabric to interface with Equilinox.

## For Players

### Prerequisites

* **Equilinox:** A valid Steam installation of Equilinox.
* **Java Runtime Environment (JRE):** Version 17 or newer is required.
* **Silk Loader Installer:** The easiest way to install Silk Loader and its dependencies.

### Installation

It is **highly recommended** to use the **[Silk Loader Installer](https://github.com/SilkLoader/silk-installer)** to install Silk Loader. The installer handles the download and setup of Silk Loader, Fabric Loader, and configures your game correctly.

**Using Mods:**
1.  Once Silk Loader is installed, a `mods` folder will be created in your Equilinox game directory (if not, manually create one).
2.  Download Fabric mods (`.jar` files) compatible with the Equilinox version you're running.
3.  Place the downloaded mod `.jar` files into the `mods` folder.
4.  Launch Equilinox through Steam (the installer will have helped you set up the launch options).

### Troubleshooting
* **Game doesn't launch / crashes on startup:**
    * Ensure you used the Silk Loader Installer correctly.
    * Remove any recently added mods one by one to identify a problematic mod.
* **Mods not loading:**
    * Ensure mods are in the correct `mods` folder.
    * Ensure Fabric Loader is actually loaded (you may have forgotten to change the launch options)

## For Mod Developers

Silk Loader allows you to develop Fabric mods for Equilinox.

### Setting up a Development Environment

* [Silk Plugin](https://github.com/SilkLoader/silk-plugin): This Gradle plugin significantly simplifies the development process. It automates tasks such as:
  * Setting up silk-loader and fabric-loader for a mod development environment.
  * Setting up a task to run the game.
  * Decompiling and recompiling the game to apply accesswideners.
* [Silk Mod Template](https://github.com/SilkLoader/equilinox-mod-template): This repository provides a ready-to-use template project. It includes:
  * A pre-configured `build.gradle.kts` using the Silk Plugin.
  * Example mod structure with entrypoint and mixin.
  * Basic setup to get you started.