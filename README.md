
# Stream2GDrive #

_Stream2GDrive_ is a simple tool that can be used to upload, download
and list files on Google Drive. It's main advantage compared to
similar tools is that it supports streaming from standard input and
output – perfect for transferring backups to and from the cloud.

It's also portable across multiple operating systems and its only
dependency is Java.

## Quickstart ##

Backup /opt to Google Drive, encrypted:

    tar cfvz - /opt | gpg -e -r you@example.com | stream2gdrive put - --output opt-2014-04-24.tar.gz

List destination folder:

    stream2gdrive list

Download it:

    stream2gdrive get opt-2014-04-24.tar.gz

Verify checksums:

    stream2gdrive md5 | md5sum -c

# Usage #

There are four commands available:

* get
* list
* md5
* put

The first time you start the program, it will ask for permission to
access your account. Normally, this will open a browser window and the
access token will be automatically retrieved using an embedded web
server.

If you're not running the command on the local computer, you can use
the <code>--oob</code> option to enter the authentication code
manually instead.

## get ##

    stream2gdrive get <name>
        [--output <local-name or '-' for stdout>]
        [--parent <remote-folder>]
        [--verbose]

Retrieve a file from your Google Drive. Specify <code>--output</code>
to override the local file name. Use <code>--parent</code> to download
a file that is not in the Google Drive root folder.

<code>--verbose</code> enables progress reporting.

## list ##

    stream2gdrive list
        [--parent <remote-folder>]

Lists files and metadata in Google Drive's root folder, or a folder
specified by <code>--parent</code>.

## md5 ##

    stream2gdrive md5
        [--parent <remote-folder>]

Lists files and their MD5 checksum in Google Drive's root folder, or a
folder specified by <code>--parent</code>.

The output is compatible with the popular _md5sum_ program and can be
piped directly to _md5sum -c_ to quickly verify that the local and
remote files are the same.

## put ##

    stream2gdrive put <local-name or '-' for stdin>
        [--output <remote-name>]
        [--parent <remote-folder>]
        [--mime <mime-type>]
        [--verbose]

Send a file to your Google Drive's root folder (unless
<code>--parent</code> is specified). Optionally specify a new remote
file name with <code>--output</code> and use <code>--mime</code> to
override the MIME type.

<code>--verbose</code> enables progress reporting.

# Author & License #

_Stream2GDrive_ was written by Martin Blom <martin@blom.org> and is
licensed under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0).
