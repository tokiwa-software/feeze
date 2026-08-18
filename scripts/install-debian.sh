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
#  Source of install-debian.sh
#
#  Provisioning of a Debian or Ubuntu test machine
#
# -----------------------------------------------------------------------

# Safe shell mode:
# -e: exit immediately if a command exits with a non-zero status
# -u: treat unset variables as an error
# -o pipefail: pipeline returns the exit status of the last command to fail
set -euo pipefail

# --- Automation Settings ---
# Force APT to use default answers and suppress interactive dialogs.
# Essential for headless installation to prevent lightdm from hanging on prompt.
export DEBIAN_FRONTEND=noninteractive

# --- Environment Variables Validation ---
# Verify that required configuration variables are passed from Vagrant/machines.yml
: "${FEEZE_VERSION:?not set — check 'release' in config/machines.yml}"
: "${FEEZE_TARGET:?not set — check 'target' for this machine}"

# --- Paths & Endpoints ---
# The GitHub release tag and asset tarball use the same naming convention.
# Suffix targets the OS distribution (e.g., Ubuntu_24), not the architecture.
FEEZE_NAME="feeze_${FEEZE_VERSION}_${FEEZE_TARGET}"
URL="https://github.com/tokiwa-software/feeze/releases/download/${FEEZE_NAME}/${FEEZE_NAME}.tar.gz"
DIR="/home/vagrant/${FEEZE_NAME}"
FILES="/tmp/feeze-files"

# --- Package Manager Pre-configuration ---
echo "=== Pre-configuring Package Manager ==="
# Pre-seed debconf selection to automatically set lightdm as the default display manager
echo "lightdm shared/default-x-display-manager select lightdm" | debconf-set-selections
apt-get update -qq

# --- Distribution Specific Resolution ---
# Resolve Package Name Discrepancies:
# Debian 13 split 'policykit-1' into 'polkitd' + 'pkexec'; Ubuntu still uses the legacy name.
# Executing within a subshell (...) prevents sourcing from overwriting local script variables.
DISTRO_ID="$(. /etc/os-release && echo "$ID")"
case "$DISTRO_ID" in
  ubuntu) POLKIT_PKGS="policykit-1" ;;
  debian) POLKIT_PKGS="polkitd pkexec" ;;
  *)      POLKIT_PKGS="polkitd" ;;
esac

# --- Package Installation ---
echo "=== Installing System Packages ==="
# Note on --no-install-recommends:
# Metapackages usually pull X server and drivers as recommendations. Omitting them
# breaks lightdm with "Can't launch X server". Every dependency is listed explicitly.
apt-get install -y --no-install-recommends \
  openjdk-25-jdk libgc1 curl tar \
  xserver-xorg xserver-xorg-core xinit \
  xserver-xorg-video-vmware xserver-xorg-video-fbdev \
  xfce4 xfce4-session xfce4-terminal \
  lightdm slick-greeter \
  libxrender1 libxtst6 libxi6 \
  $POLKIT_PKGS dbus-x11 accountsservice desktop-file-utils

# --- Dependencies Verification ---
# Explicit path calling: /sbin is omitted from standard user PATH environments on Debian.
/sbin/ldconfig
if /sbin/ldconfig -p | grep -q 'libgc\.so'; then
  echo "OK: $(/sbin/ldconfig -p | grep 'libgc\.so' | head -1)"
else
  echo "FAIL: libgc is not visible to the dynamic linker"
  exit 1
fi

# --- Display Manager & Autologin Configuration ---
echo "=== Configuring Autologin & Group Policies ==="
# Ubuntu's lightdm PAM stack strictly restricts passwordless access to members of this group.
groupadd -f nopasswdlogin
gpasswd -a vagrant nopasswdlogin

# Critical Configuration Properties in lightdm-autologin.conf:
# - user-session: Must match an entry in /usr/share/xsessions/ (prevents loop crashes).
# - greeter-session: Must match an entry in /usr/share/xgreeters/ (lightdm lacks native greeter).
mkdir -p /etc/lightdm/lightdm.conf.d
install -m 644 "$FILES/lightdm-autologin.conf" /etc/lightdm/lightdm.conf.d/50-autologin.conf

# Set system initialization target to graphical GUI mode
systemctl set-default graphical.target

# --- Security & Permissions (Polkit) ---
echo "=== Configuring Polkit Rules ==="
# Elevate privileges: The GUI "start local recorder" button relies on pkexec (PolicyKit)
mkdir -p /etc/polkit-1/rules.d
install -m 644 "$FILES/49-feeze.rules" /etc/polkit-1/rules.d/49-feeze.rules

# --- Application Deployment ---
echo "=== Downloading and Extracting Feeze ==="
# Provisioning runs as root, but application scope belongs to the 'vagrant' user.
# Skip execution if target directory exists to ensure rapid iterative provisioning runs.
if [ ! -d "$DIR" ]; then
  su - vagrant -c "cd \$HOME && curl -fL -o '${FEEZE_NAME}.tar.gz' '${URL}' && tar zxf '${FEEZE_NAME}.tar.gz'"
fi

# --- Upstream Fix / Path Patching ---
# Workaround for ldconfig binary paths:
# bin/feeze evaluates libgc via an absolute-less `ldconfig` call, failing under non-root users.
# The first regex expression strips preexisting /sbin/ prefixes to ensure execution idempotency.
sed -i -e 's#\(/sbin/\)*ldconfig#ldconfig#g' \
       -e 's#(ldconfig -p#(/sbin/ldconfig -p#' "${DIR}/bin/feeze"
grep -n 'ldconfig' "${DIR}/bin/feeze"

# --- Desktop Autostart Configuration ---
echo "=== Configuring Desktop Autostart ==="
# XFCE executes all valid desktop configurations found inside this path upon session initialization
mkdir -p /home/vagrant/.config/autostart
for f in feeze feeze-recorder; do
  sed "s#@FEEZE_DIR@#${DIR}#g" "$FILES/$f.desktop.tmpl" \
    > "/home/vagrant/.config/autostart/$f.desktop"
done

# Validate structure of generated desktop launcher profiles to catch silent validation failures
desktop-file-validate /home/vagrant/.config/autostart/feeze.desktop || echo "WARN: invalid desktop entry"
desktop-file-validate /home/vagrant/.config/autostart/feeze-recorder.desktop || echo "WARN: invalid desktop entry"

# Enforce uniform file permissions (root-owned files in home directory block user environment)
chown -R vagrant:vagrant /home/vagrant/.config

echo "Provisioning complete: OK"
