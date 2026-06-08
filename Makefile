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


FEEZE_REPO     := $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))
FEEZE_SRC      := $(FEEZE_REPO)/src
FEEZE_SRC_JAVA := $(FEEZE_SRC)/java

# the build directory is relative to the current dir
BUILD_DIR     := ./build
BUILD_OBJ     := $(BUILD_DIR)/obj
BUILD_INCLUDE := $(BUILD_DIR)/include
BUILD_CLASSES := $(BUILD_DIR)/classes

# main name
RECORDER_BIN := feeze_recorder
BPF_MAIN := feeze_recorder
C_MAIN := feeze_recorder_c
FZ_MAIN := feeze_recorder_fz

LIBBPF      := $(FEEZE_REPO)/libbpf
LIBBPF_SRC  := $(LIBBPF)/src
LIBBPF_OBJ  := $(BUILD_DIR)/libbpf_obj/libbpf.a
LIBBPF_DEST := $(BUILD_DIR)/libbpf
VMLINUX_H   := $(FEEZE_REPO)/vmlinux.h
ARCH := $(shell uname -m | sed 's/x86_64/x86/')

BPFTOOL ?= /usr/lib/linux-tools-6.8.0-124/bpftool

JAVA_SOURCES := $(shell find $(FEEZE_SRC_JAVA) -name "*.java")
JAVA_MAIN := Feeze
JAVA_MAIN_CLASSFILE := dev/flang/feeze/$(JAVA_MAIN).class
JAVA_MAIN_CLASS     := dev.flang.feeze.$(JAVA_MAIN)

.DELETE_ON_ERROR:

# build all binaries
.PHONY: all

all: $(BUILD_DIR)/bin/$(C_MAIN)

$(LIBBPF)/README.md $(VMLINUX_H)/README.md:
	@echo $@
	@echo "*** error: missing submodule libbpf and vmlinunx.h. Please do "
	@echo ""
	@echo "  > git submodule init"
	@echo "  > git submodule update"
	@echo ""
	@exit 1


# Build libbpf
$(LIBBPF_OBJ) $(LIBBPF_DEST): $(wildcard $(LIBBPF_SRC)/*.[ch] $(LIBBPF_SRC)/Makefile) $(LIBBPF)/README.md
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
$(BUILD_OBJ)/$(BPF_MAIN).bpf.o: src/bpf/$(BPF_MAIN).bpf.c $(LIBBPF_OBJ) $(VMLINUX_H)/README.md
	mkdir -p $(@D)
	clang -g -O2 -target bpf -D__TARGET_ARCH_$(ARCH)		                     \
                     -I$(FEEZE_SRC)/include                                                  \
		     -Ivmlinux.h/include/x86_64/ -I$(LIBBPF_DEST) $(CLANG_BPF_SYS_INCLUDES)  \
                     -c $(filter %.c,$^) -o $@

# Generate BPF skeletons
$(BUILD_INCLUDE)/%.skel.h: $(BUILD_DIR)/obj/%.bpf.o
	mkdir -p $(@D)
	@if [ ! -f "$(BPFTOOL)" ]; then \
	  echo "*** error: bpftool '$(BPFTOOL)' not found, please set env var BPFTOOL" >&2; \
	  exit 1; \
	fi
	$(BPFTOOL) gen skeleton $< > $@

$(BUILD_DIR)/obj/feeze_record.o: $(FEEZE_SRC)/c/feeze_record.c $(BUILD_INCLUDE)/$(BPF_MAIN).skel.h
	mkdir -p $(@D)
	clang -I$(FEEZE_SRC)/include -I$(BUILD_INCLUDE) -I$(LIBBPF_DEST) -o $@ -c $(filter %.c,$^)

$(BUILD_DIR)/obj/feeze_recorder.o: $(FEEZE_SRC)/c/feeze_recorder.c $(BUILD_INCLUDE)/$(BPF_MAIN).skel.h   # NYI: Cleanup skel.h, BPF include etc.
	mkdir -p $(@D)
	clang -I$(FEEZE_SRC)/include -I$(BUILD_INCLUDE) -I$(LIBBPF_DEST) -o $@ -c $(filter %.c,$^)

$(BUILD_DIR)/bin/$(C_MAIN): $(BUILD_DIR)/obj/feeze_recorder.o $(BUILD_DIR)/obj/feeze_record.o $(LIBBPF_OBJ)
	mkdir -p $(@D)
	clang -g -Wall $^ -lelf -lz -o $@

# Choose privilege escalation tool
PKEXEC := $(shell command -v pkexec 2>/dev/null)
SUDO   := $(shell command -v sudo 2>/dev/null)

ifeq ($(PKEXEC),)
  ifeq ($(SUDO),)
    $(error Neither pkexec nor sudo found)
  else
    ELEVATE := sudo
  endif
else
  ELEVATE := pkexec
endif

$(BUILD_DIR)/bin/$(FZ_MAIN): $(FEEZE_SRC)/fuzion/feeze_recorder.fz $(BUILD_DIR)/obj/feeze_record.o $(LIBBPF_OBJ)
	mkdir -p $(@D)
	$(FUZION_HOME)//bin/fz -c $< "-CInclude=feeze_record.h" -CFlags="-I$(FEEZE_SRC)/include  $(BUILD_DIR)/obj/feeze_record.o $(LIBBPF_OBJ) -lelf -lz"  -o=$@


# run the binary
run_recorder: $(BUILD_DIR)/bin/$(C_MAIN)
	$(ELEVATE) $(BUILD_DIR)/bin/$(C_MAIN)

$(BUILD_CLASSES)/$(JAVA_MAIN_CLASSFILE): $(JAVA_SOURCES)
	mkdir -p $(BUILD_CLASSES)
	javac -d $(BUILD_CLASSES) $^ && touch $@

build/bin/feeze: bin/feeze $(BUILD_DIR)/feeze.jmod
	mkdir -p $(@D)
	cat $< | sed "s-@MAIN_CLASS@-feeze/$(JAVA_MAIN_CLASS)-g" >$@
	chmod +x $@

$(BUILD_DIR)/bin/$(RECORDER_BIN): $(BUILD_DIR)/bin/$(C_MAIN) $(BUILD_DIR)/bin/$(FZ_MAIN)
	rm -f $@
	ln -s $(FZ_MAIN) $@

# run the GUI. NYI: to be replaced by fuzion implementation, make taret run_control
run: build/bin/feeze $(BUILD_DIR)/bin/$(RECORDER_BIN) build/bin/feeze
	./$^

$(BUILD_DIR)/feeze.jmod: $(BUILD_CLASSES)/$(JAVA_MAIN_CLASSFILE)
	rm -rf $@
	mkdir -p $(@D)
	jmod create --class-path $(BUILD_CLASSES) $@

$(BUILD_DIR)/generated/fuzion: $(BUILD_DIR)/feeze.jmod
	rm -rf $(BUILD_DIR)/generated/fuzion
	mkdir -p $(BUILD_DIR)/generated/fuzion
	FUZION_JAVA_ADDITIONAL_CLASSPATH=$(BUILD_DIR)/classes $(FUZION_HOME)/bin/fzjava -to=$(BUILD_DIR)/generated/fuzion -modules=java.base  $^
	touch $@

run_control: $(BUILD_DIR)/generated/fuzion
	@if [ ! -e $(FUZION_HOME)/bin/fz ]; then \
	  echo "*** error: fz not found, please set env var FUZION_HOME" >&2; \
	  exit 1; \
	fi
	FUZION_JAVA_ADDITIONAL_CLASSPATH=build/classes $(FUZION_HOME)/bin/fz -modules=java.base,java.datatransfer,java.xml,java.desktop -sourceDirs=src/fuzion,$(BUILD_DIR)/generated/fuzion feeze


# remove all built files
clean:
	rm -rf $(BUILD_DIR)
	find . -name "*~" -exec rm {} \;
