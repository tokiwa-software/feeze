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
: "${FEEZE_VERSION:?not set — check 'release' in config/machines.yml}"
: "${FEEZE_TARGET:?not set — check 'target' for this machine}"

# --- Paths & Endpoints ---
FEEZE_NAME="feeze_${FEEZE_VERSION}_${FEEZE_TARGET}"
URL="https://github.com/tokiwa-software/feeze/releases/download/${FEEZE_NAME}/${FEEZE_NAME}.tar.gz"
DIR="/home/vagrant/${FEEZE_NAME}"
FILES="/tmp/feeze-files"

# --- Package Installation ---
echo "=== Installing System Packages ==="
# Fedora 43 Server Edition lacks the 'xfce' group metadata.
# Packages are installed explicitly. Differences from Debian:
# - libgc1 ➔ gc
# - openjdk-25-jdk ➔ java-25-openjdk
# - policykit-1 ➔ polkit
dnf -y install \
  java-25-openjdk gc curl tar \
  xorg-x11-server-Xorg xorg-x11-xinit \
  xfwm4 xfce4-session xfce4-panel xfdesktop xfce4-terminal xfce4-settings \
  lightdm slick-greeter \
  polkit dbus-x11 accountsservice desktop-file-utils


# --- Dependencies Verification ---
echo "=== Verifying libgc Availability ==="
/sbin/ldconfig
if /sbin/ldconfig -p | grep -q 'libgc\.so'; then
  echo "OK: $(/sbin/ldconfig -p | grep 'libgc\.so' | head -1)"
else
  echo "FAIL: libgc not visible to the dynamic linker"
  exit 1
fi

# --- Debugging Window Managers ---
echo "=== Checking Installed Sessions & Greeters ==="
ls /usr/share/xsessions/ || echo "EMPTY — no session installed"
ls /usr/share/xgreeters/ || echo "EMPTY — no greeter installed"

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
echo "=== Downloading and Extracting Feeze ==="
if [ ! -d "$DIR" ]; then
  su - vagrant -c "cd \$HOME && curl -fL -o '${FEEZE_NAME}.tar.gz' '${URL}' && tar zxf '${FEEZE_NAME}.tar.gz'"
fi

# --- Desktop Autostart Configuration ---
echo "=== Configuring Desktop Autostart ==="
# Generate .desktop files by replacing the placeholders with the actual path
mkdir -p /home/vagrant/.config/autostart
for f in feeze feeze-recorder; do
  sed "s#@FEEZE_DIR@#${DIR}#g" "$FILES/$f.desktop.tmpl" \
    > "/home/vagrant/.config/autostart/$f.desktop"
done

# Validate syntax of generated desktop launcher entries
desktop-file-validate /home/vagrant/.config/autostart/feeze.desktop || echo "WARN: invalid desktop entry"
desktop-file-validate /home/vagrant/.config/autostart/feeze-recorder.desktop || echo "WARN: invalid desktop entry"

# Fix permissions for the configuration directory
chown -R vagrant:vagrant /home/vagrant/.config

echo "Provisioning complete: OK"
