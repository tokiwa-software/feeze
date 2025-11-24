# This file is part of the Feeeze scheduling analysis tool.
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
#  Source of Makefile
#
#  This is the main Makefile of the feeze tool
#
#  @author Fridtjof Siebert (siebert@tokiwa.software)
#
# -----------------------------------------------------------------------


FEEZE_REPO = $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))
FEEZE_SRC = $(FEEZE_REPO)/src

# the build directory is relative to the current dir
BUILD_DIR := ./build
BUILD_OBJ := $(BUILD_DIR)/obj
BUILD_INCLUDE := $(BUILD_DIR)/include

# main name
C_MAIN := feeze_recorder

LIBBPF_SRC := $(FEEZE_REPO)/libbpf/src
LIBBPF_OBJ := $(BUILD_DIR)/libbpf_obj/libbpf.a
LIBBPF_DEST := $(BUILD_DIR)/libbpf
ARCH := $(shell uname -m | sed 's/x86_64/x86/')

.DELETE_ON_ERROR:

# build all binaries
.PHONY: all

all: $(BUILD_DIR)/bin/$(C_MAIN)

# Build libbpf
$(LIBBPF_OBJ) $(LIBBPF_DEST): $(wildcard $(LIBBPF_SRC)/*.[ch] $(LIBBPF_SRC)/Makefile)
	mkdir -p $(dir $(LIBBPF_OBJ))
	mkdir -p $(dir $(LIBBPF_DEST))
	make -C $(LIBBPF_SRC) BUILD_STATIC_ONLY=1		            \
		    OBJDIR=$(abspath $(dir $(LIBBPF_OBJ))) DESTDIR=$(abspath $(LIBBPF_DEST))  \
		    INCLUDEDIR= LIBDIR= UAPIDIR=			    \
		    install


# from: https://github.com/libbpf/libbpf-bootstrap/blob/master/examples/c/Makefile
#
# Get Clang's default includes on this system. We'll explicitly add these dirs
# to the includes list when compiling with `-target bpf` because otherwise some
# architecture-specific dirs will be "missing" on some architectures/distros -
# headers such as asm/types.h, asm/byteorder.h, asm/socket.h, asm/sockios.h,
# sys/cdefs.h etc. might be missing.
#
# Use '-idirafter': Don't interfere with include mechanics except where the
# build would have failed anyways.
CLANG_BPF_SYS_INCLUDES ?= $(shell clang -v -E - </dev/null 2>&1 \
	| sed -n '/<...> search starts here:/,/End of search list./{ s| \(/.*\)|-idirafter \1|p }')

# Build BPF code
$(BUILD_OBJ)/$(C_MAIN).bpf.o: src/bpf/$(C_MAIN).bpf.c $(LIBBPF_OBJ)
	mkdir -p $(@D)
	clang -g -O2 -target bpf -D__TARGET_ARCH_$(ARCH)		                     \
                     -I$(FEEZE_SRC)/include                                                  \
		     -Ivmlinux.h/include/x86_64/ -I$(LIBBPF_DEST) $(CLANG_BPF_SYS_INCLUDES)  \
                     -c $(filter %.c,$^) -o $@

# Generate BPF skeletons
$(BUILD_INCLUDE)/%.skel.h: $(BUILD_DIR)/obj/%.bpf.o
	mkdir -p $(@D)
	/usr/lib/linux-tools/6.8.0-87-generic/bpftool gen skeleton $< > $@

$(BUILD_DIR)/obj/$(C_MAIN).o: $(FEEZE_SRC)/c/$(C_MAIN).c $(BUILD_INCLUDE)/$(C_MAIN).skel.h
	mkdir -p $(@D)
	clang -I$(FEEZE_SRC)/include -I$(BUILD_INCLUDE) -I$(LIBBPF_DEST) -o $@ -c $(filter %.c,$^)

$(BUILD_DIR)/bin/$(C_MAIN): $(BUILD_DIR)/obj/$(C_MAIN).o $(LIBBPF_OBJ)
	mkdir -p $(@D)
	clang -g -Wall $^ -lelf -lz -o $@

# run the binary
run: $(BUILD_DIR)/bin/$(C_MAIN)
	sudo $(BUILD_DIR)/bin/$(C_MAIN)

# remove all built files
clean:
	rm -rf $(BUILD_DIR)
	find . -name "*~" | xargs rm
