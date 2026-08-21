#!/bin/sh
# This file is part of the feeze scheduling analysis tool.
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
#  Source of install-deps.sh
#
#  This installs the toolchain needed to build and package feeze on a
#  GitHub Actions runner
#
# -----------------------------------------------------------------------
sudo apt-get update
sudo apt-get install -y \
  openjdk-25-jdk-headless libgc1 libgc-dev \
  libelf-dev \
  binutils\
  pandoc \
  "linux-tools-$(uname -r)"

JAVA_HOME="/usr/lib/jvm/java-25-openjdk-$(dpkg --print-architecture)"
test -x "$JAVA_HOME/bin/javac"
echo "JAVA_HOME=$JAVA_HOME" >>"$GITHUB_ENV"
echo "$JAVA_HOME/bin"       >>"$GITHUB_PATH"
