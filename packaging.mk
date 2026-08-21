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
#  Source of packaging Makefile
#
#  This is the Makefile for building feeze packages.
#
# -----------------------------------------------------------------------

PKG_NAME       := feeze
PKG_RELEASE    := 1
PKG_MAINTAINER := Tokiwa Software GmbH <siebert@tokiwa.software>
PKG_HOMEPAGE   := https://github.com/tokiwa-software/feeze
PKG_LICENSE    := AGPL-3.0-only
PKG_SUMMARY    := Interactive graphical thread and scheduling analysis tool using eBPF

PKG_VERSION := $(shell cat $(FEEZE_REPO)/version.txt)
PKG_FULLVERSION := $(PKG_VERSION)-$(PKG_RELEASE)

UNAME_M  := $(shell uname -m)
RPM_ARCH := $(UNAME_M)
FILE_ARCH := $(shell uname -m | sed -e 's/x86_64/x86-64/' -e 's/aarch64/ARM aarch64/')
DEB_ARCH := $(shell dpkg --print-architecture 2>/dev/null || \
                    echo $(UNAME_M) | sed -e s/x86_64/amd64/ -e s/aarch64/arm64/)

PKG_DIR   := $(BUILD_DIR)/pkg
PKG_ROOT  := $(PKG_DIR)/root
PKG_PREFIX := /usr/share/feeze
PKG_SHARE := $(PKG_ROOT)$(PKG_PREFIX)
PKG_TARDIR   := $(PKG_NAME)-$(PKG_VERSION)-$(DEB_ARCH)
PKG_DEB_FILE := $(PKG_NAME)-$(PKG_VERSION)-$(DEB_ARCH).deb
PKG_RPM_FILE := $(PKG_NAME)-$(PKG_VERSION)-$(DEB_ARCH).rpm


PKG_BINARIES := $(BUILD_DIR)/bin/feeze          \
                $(BUILD_DIR)/bin/feeze_desktop  \
                $(BUILD_DIR)/bin/$(RECORDER_BIN)

# ELF e_machine as printed by readelf, used to verify the build was native.
ELF_MACHINE := $(shell uname -m | sed -e 's/x86_64/X86-64/' -e 's/aarch64/AArch64/')

packages: $(PKG_TARDIR).tar.gz $(PKG_DEB_FILE) $(PKG_RPM_FILE)

# -----------------------------------------------------------------------
# Architecture guard: refuse to package a tree that was not built natively.
# Catches "amd64 binary inside an arm64 package" mistake early.
# -----------------------------------------------------------------------
.PHONY: pkg-check-arch
pkg-check-arch: $(BUILD_DIR)/bin/$(RECORDER_BIN)
	@readelf -h $< | grep -q '$(ELF_MACHINE)' \
	  || { echo "*** error: $< is not $(ELF_MACHINE), packages must be built natively" >&2; exit 1; }

# -----------------------------------------------------------------------
# Staging area.
#
# The code collects all Java application files into a single isolated folder usr/share/feeze,
# since it is critical for the JVM to start that binaries, classes and jmod modules
# are located together at the same level.
# -----------------------------------------------------------------------
.PHONY: pkg-stage
pkg-stage: pkg-check-arch $(PKG_BINARIES) $(FEEZE_REPO)/packaging/feeze.desktop
	rm -rf $(PKG_ROOT)
	mkdir -p $(PKG_SHARE)/bin

	install -m 755 $(BUILD_DIR)/bin/feeze         $(PKG_SHARE)/bin/feeze
	install -m 755 $(BUILD_DIR)/bin/feeze_desktop $(PKG_SHARE)/bin/feeze_desktop
	install -m 755 $(BUILD_DIR)/bin/$(RECORDER_BIN) $(PKG_SHARE)/bin/$(RECORDER_BIN)

	cp -a $(BUILD_CLASSES) $(PKG_SHARE)/classes
	install -m 644 $(BUILD_DIR)/feeze.jmod $(PKG_SHARE)/feeze.jmod
	install -m 644 $(BUILD_DIR)/icon.svg   $(PKG_SHARE)/icon.svg
	install -m 644 $(FEEZE_REPO)/README.md $(PKG_SHARE)/README.md
	install -m 644 $(FEEZE_REPO)/LICENSE   $(PKG_SHARE)/LICENSE
	mkdir -p $(PKG_ROOT)/usr/bin
	ln -sf ../share/feeze/bin/feeze              $(PKG_ROOT)/usr/bin/feeze
	ln -sf ../share/feeze/bin/$(RECORDER_BIN)    $(PKG_ROOT)/usr/bin/$(RECORDER_BIN)
	mkdir -p $(PKG_ROOT)/usr/share/applications
	install -m 644 $(FEEZE_REPO)/packaging/feeze.desktop \
	               $(PKG_ROOT)/usr/share/applications/feeze.desktop
	mkdir -p $(PKG_ROOT)/usr/share/icons/hicolor/scalable/apps
	install -m 644 $(FEEZE_REPO)/assets/logo.svg \
	               $(PKG_ROOT)/usr/share/icons/hicolor/scalable/apps/feeze.svg
	@echo " + Staged $(PKG_ROOT)"

# -----------------------------------------------------------------------
# Zero-install portable tarball
# -----------------------------------------------------------------------
$(PKG_TARDIR).tar.gz: pkg-stage
	rm -rf $(PKG_DIR)/$(PKG_TARDIR) $(PKG_TARDIR).tar.gz
	mkdir -p $(PKG_DIR)/$(PKG_TARDIR)
	cp -a $(PKG_SHARE)/. $(PKG_DIR)/$(PKG_TARDIR)/
	tar czf $(PKG_TARDIR).tar.gz -C $(PKG_DIR) $(PKG_TARDIR)
	@echo " + Created portable archive: $(PKG_TARDIR).tar.gz"

# -----------------------------------------------------------------------
# Debian package build (.deb) using raw dpkg-deb.
# -----------------------------------------------------------------------
$(PKG_DEB_FILE): pkg-stage $(FEEZE_REPO)/packaging/feeze-postinst
	rm -rf $(PKG_DIR)/deb $(PKG_DEB_FILE)
	mkdir -p $(PKG_DIR)/deb/DEBIAN
	cp -a $(PKG_ROOT)/. $(PKG_DIR)/deb/

	mkdir -p $(PKG_DIR)/deb/usr/share/doc/$(PKG_NAME)
	install -m 644 $(FEEZE_REPO)/LICENSE $(PKG_DIR)/deb/usr/share/doc/$(PKG_NAME)/copyright

	sed -e 's|@NAME@|$(PKG_NAME)|g'                                  \
	    -e 's|@VERSION@|$(PKG_FULLVERSION)|g'             \
	    -e 's|@ARCH@|$(DEB_ARCH)|g'                                  \
	    -e 's|@MAINTAINER@|$(PKG_MAINTAINER)|g'                      \
	    -e 's|@HOMEPAGE@|$(PKG_HOMEPAGE)|g'                          \
	    -e "s|@SIZE@|$$(du -ks $(PKG_DIR)/deb | cut -f1)|g"          \
	    $(FEEZE_REPO)/packaging/control.in >$(PKG_DIR)/deb/DEBIAN/control
		install -m 755 $(FEEZE_REPO)/packaging/feeze-postinst $(PKG_DIR)/deb/DEBIAN/postinst
	dpkg-deb --root-owner-group -Zzstd --build $(PKG_DIR)/deb $(PKG_DEB_FILE)
	@echo " + $(PKG_DEB_FILE)"

# -----------------------------------------------------------------------
# rpm — AutoReqProv is disabled and Requires listed by hand: automatic soname
# dependencies generated on a Debian host do not always map onto the provides
# of the target distribution.
# -----------------------------------------------------------------------
$(PKG_RPM_FILE): pkg-stage
	rm -rf $(PKG_DIR)/rpm
	mkdir -p $(PKG_DIR)/rpm/SPECS $(PKG_DIR)/rpm/BUILD $(PKG_DIR)/rpm/RPMS $(PKG_DIR)/rpm/SOURCES

	sed -e 's|@NAME@|$(PKG_NAME)|g'             \
	    -e 's|@VERSION@|$(PKG_VERSION)|g'       \
	    -e 's|@RELEASE@|$(PKG_RELEASE)|g'       \
	    -e 's|@SUMMARY@|$(PKG_SUMMARY)|g'       \
	    -e 's|@LICENSE@|$(PKG_LICENSE)|g'       \
	    -e 's|@HOMEPAGE@|$(PKG_HOMEPAGE)|g'     \
		-e 's|@PREFIX@|$(PKG_PREFIX)|g'         \
	    $(FEEZE_REPO)/packaging/feeze.spec.in >$(PKG_DIR)/rpm/SPECS/$(PKG_NAME).spec
	rpmbuild -bb                                              \
	  --define "_topdir $(abspath $(PKG_DIR)/rpm)"            \
	  --define "pkgroot $(abspath $(PKG_ROOT))"               \
	  --target $(RPM_ARCH)                                    \
	  $(PKG_DIR)/rpm/SPECS/$(PKG_NAME).spec

	# rpmbuild names the file by the canonical arch (x86_64/aarch64); rename it
	# to the unified scheme. The architecture recorded inside the package is
	# unaffected and must stay canonical, or dnf refuses to install it.
	rm -f $(PKG_RPM_FILE)
	cp $(PKG_DIR)/rpm/RPMS/$(RPM_ARCH)/$(PKG_NAME)-$(PKG_VERSION)-$(PKG_RELEASE).$(RPM_ARCH).rpm \
	   ./$(PKG_RPM_FILE)
	@echo " + $(PKG_RPM_FILE)"

.PHONY: pkg-clean
pkg-clean:
	rm -rf $(PKG_DIR)
	rm -f $(PKG_NAME)-*.tar.gz $(PKG_NAME)-*.deb $(PKG_NAME)-*.rpm
