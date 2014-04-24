/*
 * Copyright (c) 2014 Martin Blom
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.blom.martin.stream2gdrive;

import java.io.*;
import java.util.*;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.extensions.java6.auth.oauth2.GooglePromptReceiver;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.IOUtils;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.ParentReference;

public class Stream2GDrive {
    private static final String APP_NAME = "Stream2GDrive";
    private static final String APP_VERSION = "1.0";

    private static final java.io.File DATA_STORE_DIR =
        new java.io.File(System.getProperty("user.home"), ".store/drive_sample");

    public static void main(String[] args) 
        throws Exception {
        Options opt = new Options();

        opt.addOption("?",  "help",    false, "Show usage.");
        opt.addOption("V",  "version", false, "Print version information.");
        opt.addOption("p",  "parent",  true, "Operate inside this Google Drive folder instead of root.");
        opt.addOption("m",  "mime",           true, "Override guessed MIME type.");
        opt.addOption(null, "oob",           false, "Provide OAuth authentication out-of-band.");

        try {
            CommandLine cmd = new GnuParser().parse(opt, args, false);
            args = cmd.getArgs();

            if (cmd.hasOption("version")) {
                String version = "?";
                String date    = "?";

                try {
                    Properties props = new Properties();
                    props.load(resource("/build.properties"));

                    version = props.getProperty("version", "?");
                    date    = props.getProperty("date", "?");
                }
                catch (Exception ignored) {}

                System.err.println(String.format("%s %s. Build %s (%s)",
                                                 APP_NAME, APP_VERSION, version, date));
                System.err.println();
            }

            if (cmd.hasOption("help")) {
                throw new ParseException(null);
            }

            if (args.length < 1) {
                throw new ParseException("<cmd> missing");
            }

            String command = args[0];

            JsonFactory          jf = JacksonFactory.getDefaultInstance();
            HttpTransport        ht = GoogleNetHttpTransport.newTrustedTransport();
            GoogleClientSecrets gcs = GoogleClientSecrets.load(jf, resource("/client_secrets.json"));

            Set<String> scopes = new HashSet<String>();
            scopes.add(DriveScopes.DRIVE_FILE);
            scopes.add(DriveScopes.DRIVE_METADATA_READONLY);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(ht, jf, gcs, scopes)
                .setDataStoreFactory(new FileDataStoreFactory(DATA_STORE_DIR))
                .build();

            VerificationCodeReceiver vcr = !cmd.hasOption("oob")
                ? new LocalServerReceiver()
                : new GooglePromptReceiver();
        
            Credential creds = new AuthorizationCodeInstalledApp(flow, vcr)
                .authorize("user");

            Drive client = new Drive.Builder(ht, jf, creds)
                .setApplicationName(APP_NAME + "/" + APP_VERSION)
                .build();

            String root = null;

            if (cmd.hasOption("parent")) {
                root = findWorkingDirectory(client, cmd.getOptionValue("parent"));
            }
            
            if (command.equals("get")) {
                String remote;
                String local;
 
                if (args.length < 2) {
                    throw new ParseException("<file> missing");
                }
                else if (args.length == 2) {
                    remote = local = args[1];
                }
                else if (args.length == 3) {
                    remote = args[1];
                    local  = args[2];
                }
                else {
                    throw new ParseException("Too many arguments");
                }

                String link = findFileURL(client, remote, root == null ? "root" : root);

                InputStream is = client.getRequestFactory().buildGetRequest(new GenericUrl(link))
                    .execute()
                    .getContent();

                if (local.equals("-")) {
                    IOUtils.copy(is, System.out, false);
                }
                else {
                    IOUtils.copy(is, new FileOutputStream(local));
                }
            }
            else if (command.equals("put")) {
                String file;
                String name;
 
                if (args.length < 2) {
                    throw new ParseException("<file> missing");
                }
                else if (args.length == 2) {
                    file = args[1];
                    name = new File(file).getName();
                }
                else if (args.length == 3) {
                    file = args[1];
                    name = args[2];
                }
                else {
                    throw new ParseException("Too many arguments");
                }

                com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
                meta.setTitle(cmd.getOptionValue("name", name));
                meta.setMimeType(cmd.getOptionValue("mime", new javax.activation.MimetypesFileTypeMap().getContentType(file)));

                if (root != null) {
                    meta.setParents(Arrays.asList(new ParentReference().setId(root)));
                }

                AbstractInputStreamContent isc = file.equals("-")
                    ? new StreamContent(meta.getMimeType(), System.in)
                    : new FileContent(meta.getMimeType(), new File(file));

                Drive.Files.Insert insert = client.files().insert(meta, isc);
                MediaHttpUploader uploader = insert.getMediaHttpUploader();
                uploader.setDirectUploadEnabled(false);
                uploader.setChunkSize(10 * 1024 * 1024);

                // Streaming upload with GZip encoding has horrible performance!
                insert.setDisableGZipContent(isc instanceof StreamContent);
                insert.execute();
            }
            else if (command.equals("md5") || command.equals("list")) {
                for (com.google.api.services.drive.model.File file : client.files().list()
                         .setQ(String.format("'%s' in parents and mimeType!='application/vnd.google-apps.folder' and trashed=false",
                                             root == null ? "root" : root))
                         .execute()
                         .getItems()) {
                    if (command.equals("md5")) {
                        System.out.println(String.format("%s *%s", file.getMd5Checksum(), file.getTitle()));
                    }
                    else {
                        System.out.println(String.format("%-29s %-19s %12d %s %s", 
                                                         file.getMimeType(), file.getLastModifyingUserName(),
                                                         file.getFileSize(), file.getModifiedDate(), file.getTitle()));
                    }
                }
            }
            else {
                throw new ParseException("Invalid command: " + command);
            }
        }
        catch (ParseException ex) {
            PrintWriter   pw = new PrintWriter(System.err);
            HelpFormatter hf = new HelpFormatter();

            hf.printHelp(pw, 80, APP_NAME + " [OPTIONS] <cmd> [<options>]",
                         "  Commands: get <file> [<local-file-name>], list, md5, put <file> [<remote-file-name>].",
                         opt, 2, 8,
                         "Use '-' as <file> for standard input.");

            if (ex.getMessage() != null && !ex.getMessage().isEmpty()) {
                pw.println();
                hf.printWrapped(pw, 80, String.format("Error: %s.", ex.getMessage()));
            }

            pw.flush();
        }
        catch (IOException ex) {
            System.err.println("I/O error: " + ex.getMessage());
        }
    }

    private static String findWorkingDirectory(Drive client, String name)
        throws IOException {

        List<com.google.api.services.drive.model.File> folder = client.files().list()
            .setQ(String.format("title='%s' and mimeType='application/vnd.google-apps.folder' and trashed=false", name))
            .execute()
            .getItems();

        if (folder.size() == 0) {
            throw new IOException(String.format("Folder '%s' not found", name));
        }
        else if (folder.size() != 1) {
            throw new IOException(String.format("Folder '%s' matched more than one folder", name));
        }
        else {
            return folder.get(0).getId();
        }
    }

    private static String findFileURL(Drive client, String name, String parent)
        throws IOException {
        List<com.google.api.services.drive.model.File> file = client.files().list()
            .setQ(String.format("title='%s' and '%s' in parents and mimeType!='application/vnd.google-apps.folder' and trashed=false", name, parent))
            .execute()
            .getItems();

        if (file.size() == 0) {
            throw new IOException(String.format("File '%s' not found", name));
        }
        else if (file.size() != 1) {
            throw new IOException(String.format("File '%s' matched more than one document", name));
        }
        else {
            return file.get(0).getDownloadUrl();
        }
    }

    private static Reader resource(String name)
        throws IOException {
        return new InputStreamReader(Stream2GDrive.class.getResourceAsStream(name));
    }

    private static class StreamContent
        extends AbstractInputStreamContent {
        private InputStream is;

        public StreamContent(String type, InputStream is) {
            super(type);
            this.is = is;
        }

        @Override public InputStream getInputStream() {
            return is;
        }

        @Override public boolean retrySupported() {
            return false;
        }

        @Override public long getLength() {
            return -1;
        }
    }
}
