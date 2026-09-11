#!/usr/bin/env bash
# This file is part of the Feeze scheduling analysis tool.
#
# This code is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License, version 3,
# as published by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License, version 3,
# along with this program.  If not, see <http://www.gnu.org/licenses/>


# -----------------------------------------------------------------------
#
#  Copyright (c) 2025, Tokiwa Software GmbH, Germany
#
#  Source of install-fedora.sh
#
#  Provisioning of a Fedora test machine
#
#
# -----------------------------------------------------------------------

# Provisioning script for Fedora: installs the feeze release and a minimal
# XFCE desktop so that the GUI can be tested inside a VirtualBox VM.
#
# Safe shell mode:
# -e: exit immediately if a command exits with a non-zero status
# -u: treat unset variables as an error
# -o pipefail: return value of a pipeline is the status of the last command to exit with a non-zero status
set -euo pipefail

# --- Environment Variables Validation ---
# Verify that required configuration variables are passed from Vagrant/machines.yml
: "${FEEZE_ARCH:?not set — check 'arch' for this machine}"

# --- Paths & Endpoints ---
URL="https://github.com/tokiwa-software/feeze/releases/download/snapshot/feeze-${FEEZE_VERSION}-${FEEZE_ARCH}.rpm"
FILES="/tmp/feeze-files"

# --- Package Installation ---
echo "=== Installing System Packages ==="
# Fedora 43 Server Edition lacks the 'xfce' group metadata.
# Packages are installed explicitly. Differences from Debian:
# - libgc1 ➔ gc
# - openjdk-25-jdk ➔ java-25-openjdk
# - policykit-1 ➔ polkit
dnf -y install \
  java-25-openjdk gc curl  \
  xorg-x11-server-Xorg xorg-x11-xinit \
  xfwm4 xfce4-session xfce4-panel xfdesktop xfce4-terminal xfce4-settings \
  lightdm slick-greeter \
  polkit dbus-x11 accountsservice desktop-file-utils

# --- Debugging Window Managers ---
echo "=== Checking Installed Sessions & Greeters ==="
ls /usr/share/xsessions/ || echo "EMPTY — no session installed"
ls /usr/share/xgreeters/ || echo "EMPTY — no greeter installed"

# --- Dependencies Verification ---
echo "=== Verifying libgc Availability ==="
/sbin/ldconfig
if /sbin/ldconfig -p | grep -q 'libgc\.so'; then
  echo "OK: $(/sbin/ldconfig -p | grep 'libgc\.so' | head -1)"
else
  echo "FAIL: libgc not visible to the dynamic linker"
  exit 1
fi

# --- Display Manager & Autologin Configuration ---
echo "=== Configuring Autologin ==="
# Unlike Debian/Ubuntu, Fedora's lightdm PAM configuration does not require
# a 'nopasswdlogin' group. Autologin works solely via lightdm.conf configuration.
mkdir -p /etc/lightdm/lightdm.conf.d
install -m 644 "$FILES/lightdm-autologin.conf" /etc/lightdm/lightdm.conf.d/50-autologin.conf


# Set system to boot into GUI mode and enable the display manager service
systemctl set-default graphical.target
systemctl enable lightdm

# --- Security & Permissions (Polkit & Sudoers) ---
echo "=== Configuring Polkit & Permissions ==="
# The GUI's "start local recorder" button uses pkexec (PolicyKit), not sudo.
mkdir -p /etc/polkit-1/rules.d
install -m 644 "$FILES/49-feeze.rules" /etc/polkit-1/rules.d/49-feeze.rules

# --- Application Deployment ---
echo "=== Installing Feeze ==="
curl -fL -o /tmp/feeze.rpm "$URL"
dnf -y install /tmp/feeze.rpm

echo "=== Dependency Check ==="
# The .rpm is built on an ubuntu-24.04 runner, so it links against Ubuntu's
# glibc. This is where a portability problem against Fedora's would show up.
BIN=/usr/share/feeze/bin/feeze_recorder
if ldd "$BIN" | grep 'not found'; then
  echo "FAIL: missing libraries"
  exit 1
fi
echo "OK: all shared libraries resolved"

echo "=== Configuring Autostart ==="
mkdir -p /home/vagrant/.config/autostart
install -m 644 "$FILES/feeze.desktop"          /home/vagrant/.config/autostart/feeze.desktop
install -m 644 "$FILES/feeze-recorder.desktop" /home/vagrant/.config/autostart/feeze-recorder.desktop

# Validate syntax of generated desktop launcher entries
desktop-file-validate /home/vagrant/.config/autostart/feeze.desktop || echo "WARN: invalid desktop entry"
desktop-file-validate /home/vagrant/.config/autostart/feeze-recorder.desktop || echo "WARN: invalid desktop entry"

# Fix permissions for the configuration directory
chown -R vagrant:vagrant /home/vagrant/.config

echo "Provisioning complete: OK"
