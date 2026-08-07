<img src="images/logo.svg" alt="feeze logo" width="60" />

# feeze User Manual

Feeze is an interactive graphical thread and scheduling analysis tool using eBPF.

## Download and Installation

### Feeze Releases Download

Downloadable releases will be published on GitHub under
[feeze releases](https://github.com/tokiwa-software/feeze/releases).

### Installation from tarball

Installing feeze from a tarball gives you more control over where feeze will get
installed. You will need to unpack the archive using

    # tar zxf feeze_VERSION_TARGET.tar.gz

where `VERSION` is the feeze version and `TARGET` is the
target Linux version it was built for. For version `0.001dev` built
for `Ubuntu_24`, you will have to use

    # tar zxf feeze_0.001dev_Ubuntu_24.tar.gz

The result will be a directory with a name like `feeze_VERSION_TARGET`, so in
our example of version `0.001dev` build for `Ubuntu_24` the directory name will
be `feeze_0.001dev_Ubuntu_24`.

### Required Dependencies

To run feeze, you will need to install

* OpenJDK version 25 or later

  The Java environment used for the GUI, it must not be headless

  On Ubuntu, do

      > sudo apt update
      > sudo apt install -y openjdk-25-jdk

  on Fedora,

      > sudo dnf install java-25-openjdk.x86_64

  To set the PATH, you might have to do

      > export PATH=/usr/lib/jvm/jre-25/bin:$PATH

  or similar.

* libgc1.so

  To install libgc.so, on Ubuntu, do

      > sudo apt update
      > sudo apt install libgc

  on Fedora, do

      > sudo dnf install libgc

  This is the Boehm-Demers-Weiser's GC that is currently used by Fuzion until an
  exact GC is available.

* BPF tools

### Running

To start the feeze GUI, run the script `feeze` in the `bin` directory of the installation, e.g., using

    # ./feeze_0.001dev_Ubuntu_24/bin/feeze

## Feeze Control Window

Once started, the feeze control window is opened.

<img src="images/control_window.png" alt="sample control window" width="638" />

This window provides controls to start the feeze record, to configure the
communication and recording, to start and stop recording and to open a scheduler
data window.

### Starting the feeze recorder

Before you can start recording scheduler events, you will have to start the
`feeze_recorder` with superuser privileges. You can do this using the `start
local recoder` button, a prompt for your password will appear. Note that you
will need the rights to perform `sudo` for this.

NYI: UNDER DEVELOPMENT: Currently, the feeze recorder must run on the same
machine as the feeze GUI. It is planned to permit running the recorder on
another machine and communicate via a network connection.

### Configuring the fuzion installation

For programs written in the Fuzion language, feeze may display trace points that
were added to the Fuzion source code using base module features
`fuzion.runtime.trace(col u8, msg String)` and `fuzion.runtime.trace(msg
String)`. To use these, trace points must be set in the fuzion's runtime
library. For this to work, the path to the Fuzion installation must be given via
the button `Fuzion home directory`.

### Configuring shared memory communication

The feeze recorder communicates with the feeze GUI via shared memory that is
identified by a file name.  The name of this file must be given as `Shared
memory file`. This file may also be used to store scheduling data for later.

Additionally, a maximum size of the shared memory may be given. Trace data may
quickly grow very large, in particular when there are frequent thread
interactions or user trace events.  The `Shared memory size (KB/MB)` field
permits setting the size of this shared memory.  Once a recording has reached
this size, the recording will stop.

Note that overwriting the data in the shared memory file, e.g., by re-starting
the recorder while the date from an earlier trace is being displayed, may result
in corrupting the displayed data.

### Recording Scheduling Data

The `record` button or the key combination <kbd>Alt</kbd>+<kbd>R</kbd> will start recording scheduling
data.  While recording, a blue bar next to `Used Memory:` will display the
amount of shared memory that is used by the recorded data.  This bar will grow
continuously with the recording.  While recording, the `record` button is
changed into a `stop` button. This button or hitting <kbd>Alt</kbd>+<kbd>R</kbd> again will stop the
current recording.

If not stopped explicitly, the recording will stop automatically once the shared
memory buffer is full.

### Displaying recorded data

Once the scheduling data has been recorded, the data can be displayed using the
`show data` button.  This will result in opening a `Feeze Scheduling Data`
window.  See [Scheduling Data Window](#Scheduling-Data-Window) below for details.

### Control Window Keyboard shortcuts

The following key-combination may be used as shortcuts:

* <kbd>Alt</kbd>+<kbd>S</kbd> for `start local recorder` button

* <kbd>Alt</kbd>+<kbd>R</kbd> for `record` button

* <kbd>Alt</kbd>+<kbd>D</kbd> for `show data` button

## Scheduling Data Window

The scheduling data window looks like this:

<img src="images/scheduling_data_window2.png" alt="sample scheduling data window" width="789" />

### detailed vs. cumulative user / process / thread views

The number of threads recorded can be overwhelmingly large. To reduce the amount
of data displayed, there are cumulative views per user, per process (NYI: UNDER
DEVELOPMENT) and per thread as follows:

#### cumulative per-user view

Originally, the scheduling data window it will display cumulative date per
user. In this cumulative view, all processes and threads for one user will be
collapsed into a single line.  E.g., for the root user, the cumulative view may
look like this:

<img src="images/scheduling_data_window_cumulative_root.png" alt="sample scheduling data window showing cumulative root user" width="569" />

Here, a single horizontal line is labeled `all root` shows the cumulative
activity for user `root`.

To graphically distinguish users easily, the background color alternates between
lilac and blue for different users.

#### cumulative per-process view

NYI: UNDER DEVELOPMENT: It is planned to expand the GUI to show cumulative scheduling data for each process.

To graphically distinguish processes easily, the background color user for processes alternates between
different shades of the underlying user color.

#### per-thread view

Using the small button on the left of a user

<img src="images/scheduling_data_window_cumulative_user_button.png" alt="sample scheduling data window showing threads of root user" width="62" />

you can enable the detailed per-thread view for this user

<img src="images/scheduling_data_window_detailed_user_button.png" alt="sample scheduling data window showing un-collapse user button" width="62" />

which shows all the recorded processes of that user and their threads:

<img src="images/scheduling_data_window_threads_root.png" alt="sample scheduling data window showing threads collapse user button" width="570" />

Each thread will be shown as one horizontal line.

Note the the `feeze_recorder` process and thread will also be shown, this is the process that collects and stores the scheduling data.

### Time resolution and thread collapsing

You can expand and compress the time resolution using the left or middle mouse
buttons (see [Mouse Buttons](#Scheduling-Data-Window-Mouse-Buttons)) or by
clicking on the buttons labeled `🠊🠈`/`🠈🠊`.  Holding the left mouse button, you
can drag the displayed area.

As you do this, the displayed data will be adjusted dynamically to reduce the
space taken by inactive threads and processes such that more active threads and
processes fit on the visible area:

<img src="images/scheduling_data_window_threads_collapsed.png" alt="sample scheduling data window showing collapsed threads" width="792" />

Here, only processes `Xwaylend`, `firefox`, `gimp`, and `Isolated Web Co
(Isolate)` are shown in detail, while threads of other processes are collapsed
to thin horizontal lines.

### Thread state Display

Thread states are visualized by thickness and color of the horizontal line drawn
for a thread. Here is an enlarged example:

<img src="images/scheduling_data_window_thread_states.png" alt="sample scheduling data window showing thread states" width="760" />

The meaning of the colors in detail is

* very thin horizontal gray line: an inactive thread that is sleeping/blocked.

* thin horizontal light-blue line: `waking` a thread that was woken up from
  being sleeping/blocked, but not yet added to a CPU's ready queue

* horizontal blue bar: `wakesup` a thread that is ready to run waiting in a
  CPU's ready queue.

* thick horizontal green bar: `running` a thread that is running on a CPU.

Thread state changes that are caused by different threads are shown
using blue arrows from the thread performing the state change to the affected
thread.

If thread state changes occur too frequently to be displayed at the current time
resolutions, the state changes will not be drawn but collapsed into dark green
areas. These dark green areas illustrate that you need to expand the time
resolution here to see what is actually happening:

<img src="images/scheduling_data_window_state_collapse.png" alt="sample scheduling data window showing collapsed thread states" width="481" />

Note that blue arrows showing the thread causing a state change will not be
shown in the case of collapsed state changes due to too coarse time resolution.

Independent of the time scale, if it is too coarse, these dark green bars will
always be shown if there was some thread activity in the affected thread, even
if this activity lasted for a microscopically small interval considering the
displayed time resolution. This is done to ensure that at any time resolution, a
thread that is shown as sleeping was completely inactive at the displayed time.
On the flip-side, this means that using a highly compressed time resolution, you
will see many threads in full width.

### Thread Tool Tips

Tool tips are used to display more detailed information about a thread state.
To show this information, point the cursor on the thread state of interest and
leave it there for a short time.  A tool tip like the following will appear:

<img src="images/scheduling_data_window_tooltip.png" alt="sample scheduling data window showing thread tool tip" width="448" />

Here, the tool tip shows the thread name `Xwayland:cs0`, its state `RUNNING` and
the overall time that the thread is in this state `100µs 598ns`, the CPU it is
running on `CPU14` and the point in time since the start of the recording that
we are looking at `2s 167ms 974µs 688ns`.

### Scheduling Data Window Mouse Buttons

#### Time Scale

Expanding the time scale helps to get a more detailed view of what is
happening, while compressing gives an overview over longer periods.

* <kbd>Left Mouse Button</kbd> click/hold: expand time scale one step / repeatedly

* <kbd>Middle Mouse Button</kbd> click/hold: compress time scale one step / repeatedly

#### Zoom

Zoom helps to increase or shrink the size of the overall displayed graph. This
helps to get the best compromise between readability and amount of data
displayed on the screen.  Note that zooming in our out does not change the
detail of information that is shown, it only changes the size of the graphical
elements.

* <kbd>Shift</kbd> + <kbd>Left Mouse Button</kbd> click/hold: zoom in one step / repeatedly

* <kbd>Shift</kbd> + <kbd>Middle Mouse Button</kbd> click/hold: zoom out one step / repeatedly

#### Dragging

Dragging the displayed data area allows moving along the time axis
(horizontally) and the user/processes/threads axis (vertically):

* left button hold and move to drag the displayed area

### Scheduling Data Window Keyboard shortcuts

The following key-combination may be used as shortcuts:

* <kbd>Alt</kbd>+<kbd>X</kbd> expand time one step

* <kbd>Alt</kbd>+<kbd>C</kbd> compress time one step

* <kbd>Alt</kbd>+<kbd>Z</kbd> zoom in one step

* <kbd>Alt</kbd>+<kbd>O</kbd> zoom out one step

* <kbd>Ctrl</kbd>+<kbd>W</kbd> close window

* <kbd>Ctrl</kbd>+<kbd>Q</kbd> quite feeze GUI

Have fun!

<!--  LocalWords:  img src feeze eBPF zxf dev OpenJDK libgc Boehm Demers GC NYI Weiser's Fuzion recoder sudo fuzion msg fuzion's Xwaylend firefox wakesup Ctrl  -->
