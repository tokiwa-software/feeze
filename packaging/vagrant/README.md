# Testing feeze releases in virtual machines

Brings up one virtual machine per Linux distribution, installs a published
feeze release into it and starts the GUI, so a release can be checked somewhere
other than a development machine.

The recorder only writes scheduling data while the GUI is running, so testing a
release means having a real X session. Getting one up in an otherwise headless
box is most of what these scripts do.

## Requirements

VirtualBox, Vagrant, ~2 GB of free RAM per running machine and ~10 GB of disk.
`sudo apt update && sudo apt install -y vagrant virtualbox`

All commands are run from packaging/vagrant/.

## Usage

```sh
vagrant up ubuntu         # create + boot (first run downloads the box, slow)
vagrant ssh ubuntu        # log into the machine
vagrant provision ubuntu  # re-run the provisioning script only
vagrant halt ubuntu       # graceful shutdown
vagrant destroy ubuntu    # delete the machine completely
```

The VirtualBox window opens straight into an XFCE session with feeze already
running. Bring up one machine at a time — `vagrant up` with no argument starts
all of them, and a host with 8 GB will start swapping and time out during boot.

### Vagrant Reload Required on First Boot
>  **Note:** Upon the initial creation of the virtual machine, a `vagrant reload` may be required to properly initialize the Ubuntu environment.

If the VM behaves unexpectedly (e.g., the desktop does not appear) after the initial `vagrant up`, run:

```sh
vagrant reload
```

Always remove machines with `vagrant destroy`, never by deleting `.vagrant/`.
Vagrant tracks its machines in that directory and VirtualBox keeps its own
registry; clearing one without the other leaves them out of step:

```
A VirtualBox machine with the name 'ubuntu' already exists.
```

The machine is still registered in VirtualBox while Vagrant has forgotten it.
Unregister it directly, then try again:

```sh
VBoxManage list vms                      # what VirtualBox still knows about
VBoxManage controlvm ubuntu poweroff     # only if it is still running
VBoxManage unregistervm ubuntu --delete  # remove it along with its disks
```

## Layout

```
Vagrantfile                     reads the config, defines one machine per entry
config/machines.yml             release version, boxes, per-machine settings
scripts/install-debian.sh       provisioning for Debian and Ubuntu
scripts/install-fedora.sh       provisioning for Fedora
scripts/files/                  files installed into the machines
```

Vagrant copies only the provisioning script into a machine, so `scripts/files/`
is mounted separately at `/tmp/feeze-files`.

| File in `scripts/files/`      | Installed as                                    |
| ----------------------------- | ----------------------------------------------- |
| `lightdm-autologin.conf`      | `/etc/lightdm/lightdm.conf.d/50-autologin.conf` |
| `49-feeze.rules`              | `/etc/polkit-1/rules.d/49-feeze.rules`          |
| `feeze.desktop`               | `~/.config/autostart/feeze.desktop`             |
| `feeze-recorder.desktop`      | `~/.config/autostart/feeze-recorder.desktop`    |


## Adding a distribution

Add an entry to `config/machines.yml`:

```yaml
machines:
  - name: "debian"
    box: "bento/debian-13"
    family: "debian"        # selects scripts/install-debian.sh
    arch: "amd64"           # which package to download
    memory: 4096            # optional, overrides the defaults section
```

`family` picks the provisioning script, so a distribution sharing a package
manager with an existing one needs no new script. A new package manager needs
one `install-<family>.sh`; the two existing scripts are the template.

`arch` selects the package to download from the snapshot release, where the
file name carries the architecture — feeze-0.001-amd64.deb.

## What the provisioning does

1. Installs a minimal XFCE desktop with lightdm, plus the packages the feeze
   package itself does not pull in.
2. Configures passwordless autologin for the `vagrant` user.
3. Downloads the release package and installs it with the system package
   manager, which resolves the runtime dependencies.
4. Writes autostart entries so the GUI comes up with the session.

Several steps verify their own result.

## Notes

- **pkexec is not sudo.** The GUI's "start local recorder" button goes through
  PolicyKit, which sudo rules do not cover — hence `49-feeze.rules`. It lets the vagrant user run anything through pkexec without a password, so it belongs in a throwaway VM only.
- **Package names differ.** `libgc1`/`gc`, `openjdk-25-jdk`/`java-25-openjdk`,
  `policykit-1`/`polkitd`+`pkexec` on Debian 13/`polkit`. Fedora 43 Server has
  no XFCE group, so those packages are listed one by one.
- **`--no-install-recommends`.** The X server, video drivers and greeter come
  in as recommends of the desktop metapackages, so each one is named
  explicitly; otherwise lightdm cannot start an X server at all.
- **lightdm needs both names.** `user-session` must match a file in
  `/usr/share/xsessions/`, `greeter-session` one in `/usr/share/xgreeters/`.
  Ubuntu additionally requires the `nopasswdlogin` group; Fedora does not.
