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
#  Source of test-package.sh
#
#  This installs a built feeze package inside a clean container and checks
#  that it is usable. Runs inside the container, not on the runner.
#
# -----------------------------------------------------------------------

set -eux

if [ -f /etc/debian_version ]; then
  apt-get update
  apt-get install -y ./*.deb
else
  dnf install -y ./*.rpm
fi

command -v feeze
command -v feeze_recorder

# Swing needs a display, so assert only that the preflight checks in bin/feeze
# pass - that covers the JDK 25 and libgc dependencies.
feeze >/tmp/out 2>&1 || true
if grep -qE "not found in .PATH|must be at least 25|libgc.so not installed" /tmp/out; then
  cat /tmp/out
  exit 1
fi
