/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util

import org.apache.commons.fileupload._
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.RequestGenerator
import java.io.InputStream
import java.io.OutputStream
import java.util.{Map => JMap, HashMap => JHashMap}

import scala.collection.JavaConversions._
import org.orbeon.oxf.pipeline.api.ExternalContext.Session
import util.Streams

/**
 * Multipart decoding with support for progress indicator.
 */
object Multipart {

    private val UPLOAD_PROGRESS_SESSION_KEY = "orbeon.upload.progress."
    private val STANDARD_PARAMETER_ENCODING = "utf-8"
    private val COPY_BUFFER_SIZE = 8192

    /**
     * Decode a multipart/form-data stream and return a Map of parameters of type Object[], each of
     * which can be a String or FileItem.
     */
    def getParameterMapMultipart(pipelineContext: PipelineContext, request: ExternalContext.Request, headerEncoding: String): JMap[String, Array[AnyRef]] = {

        require(pipelineContext ne null)
        require(request ne null)
        require(headerEncoding ne null)

        val uploadParameterMap = new JHashMap[String, Array[AnyRef]]

        // Read properties
        // NOTE: We use properties scoped in the Request generator for historical reasons. Not too good.
        val maxSize = RequestGenerator.getMaxSizeProperty
        val maxMemorySize = RequestGenerator.getMaxMemorySizeProperty

        val diskFileItemFactory = new DiskFileItemFactory(maxMemorySize, SystemUtils.getTemporaryDirectory)
        val upload = new ServletFileUpload(diskFileItemFactory)

        upload.setHeaderEncoding(headerEncoding)
        upload.setSizeMax(maxSize)

        // Add a listener to destroy file items when the pipeline context is destroyed
        pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter {
            override def contextDestroyed(success: Boolean) =
                for ((name, value) <- uploadParameterMap if value.isInstanceOf[FileItem])
                    runQuietly(value.asInstanceOf[FileItem].delete())
        })

        // Parse the request and add file information
        useAndClose(request.getInputStream) { inputStream =>
            val requestContext = new RequestContext {
                def getContentType = request.getContentType
                def getContentLength = request.getContentLength
                def getCharacterEncoding = request.getCharacterEncoding

                // Q: Is this note still up to date?
                // NOTE: The upload code does not actually check that it doesn't read more than the content-length
                // sent by the client! Maybe here would be a good place to put an interceptor and make sure we
                // don't read too much.
                def getInputStream = inputStream
            }

            parseRequest(requestContext, diskFileItemFactory, upload, request, uploadParameterMap)
        }

        uploadParameterMap
    }

    def getUploadProgressJava(request: ExternalContext.Request, uuid: String) =
        getUploadProgress(request, uuid) match {
            case Some(progress) => progress
            case _ => null
        }

    def getUploadProgress(request: ExternalContext.Request, uuid: String): Option[UploadProgress] =
        request.getSession(false) match {
            case session: Session => session.getAttributesMap.get(UPLOAD_PROGRESS_SESSION_KEY + uuid) match {
                case progress: UploadProgress => Some(progress)
                case _ => None
            }
            case _ => None
        }

    /* NOTE: Don't remove the object from the session. This handles the following case:
             o upload 2 starts and puts new UploadProgress into session
             o upload 1 done event sent to server
             o UploadProgress is removed from session
             o upload 2 keeps updating UploadProgress but this is not put back into session
             o so no progress updates are sent to the client

    def removeUploadProgress(request: ExternalContext.Request, uuid: String): Unit =
        request.getSession(false) match {
            case session: Session => session.getAttributesMap.remove(UPLOAD_PROGRESS_SESSION_KEY + uuid)
            case _ =>
        }
    */

    class UploadProgress(val fieldName: String, val expectedSize: Option[Long]) {
        var receivedSize = 0L
        var completed = false
    }

    private def parseRequest(requestContext: RequestContext, factory: DiskFileItemFactory, upload: ServletFileUpload,
                             request: ExternalContext.Request, uploadParameterMap: JMap[String, Array[AnyRef]]): Unit = {

        require(requestContext ne null)
        require(factory ne null)
        require(upload ne null)
        require(request ne null)
        require(uploadParameterMap ne null)

        // Don't create session if missing
        val session = request.getSession(false)

        var progressUUID: String = null

        val items = collection.mutable.Seq[FileItem]()
        var successful = false

        try {
            for (item <- upload.getItemIterator(requestContext)) {
                val fileItem = factory.createItem(item.getFieldName, item.getContentType, item.isFormField, item.getName)

                // Make sure openStream is opened otherwise other methods fail later due to NPE
                val inputStream = item.openStream

                items :+ fileItem                

                val fieldValue=
                    if (fileItem.isFormField) {
                        // Simple form field
                        // Assume that form fields are in UTF-8. Can they have another encoding? If so, how is it specified?
                        val formFieldValue = Streams.asString(inputStream, STANDARD_PARAMETER_ENCODING)
                        if ((progressUUID eq null) && (session ne null) && item.getFieldName == "$uuid") {
                            // NOTE: The test on $uuid is XForms-engine specific
                            progressUUID = formFieldValue
                        }

                        formFieldValue
                    } else if (progressUUID ne null) {
                        // File upload with progress notification

                        // Get expected size first from part then from request
                        val expectedLength = item.getHeaders match {
                            case headers: FileItemHeaders if headers.getHeader("content-length") ne null => Some(headers.getHeader("content-length").toLong)
                            case _ => request.getHeaderMap.get("content-length") match {
                                case requestLength: String => Some(requestLength.toLong)
                                case _ => None
                            }
                        }

                        // Store into session with start value
                        val uploadProgress = new UploadProgress(item.getFieldName, expectedLength)
                        session.getAttributesMap.put(UPLOAD_PROGRESS_SESSION_KEY + progressUUID, uploadProgress)

                        copyStream(inputStream, fileItem.getOutputStream, true, (read: Long) => {
                            // Update session value
                            uploadProgress.receivedSize += read
                        })

                        uploadProgress.completed = true

                        fileItem
                    } else {
                        // File upload without progress notification -> just copy the stream
                        copyStream(item.openStream, fileItem.getOutputStream, true)

                        fileItem
                    }

                if (fileItem.isInstanceOf[FileItemHeadersSupport])
                    (fileItem.asInstanceOf[FileItemHeadersSupport]).setHeaders(item.getHeaders)

                StringConversions.addValueToObjectArrayMap(uploadParameterMap, fileItem.getFieldName, fieldValue)
            }
            successful = true
        } finally {
            if (!successful) {
                // Remove session value
                if (progressUUID ne null)
                    session.getAttributesMap.remove(UPLOAD_PROGRESS_SESSION_KEY + progressUUID)

                // Free underlying storage for all (disk) items gathered so far
                for (fileItem <- items)
                    runQuietly(fileItem.delete())
            }
        }
    }

    def copyStream(in: InputStream, out: OutputStream, closeOut: Boolean, progress: (Long) => Unit = null) = {

        require(in ne null)
        require(out ne null)

        useAndClose(in) { in =>
            useAndClose(out) { out =>
                val buffer = new Array[Byte](COPY_BUFFER_SIZE)
                Iterator continually (in read buffer) takeWhile (_ != -1) foreach { read =>
                    if (read > 0) {
                        if (progress ne null)
                            progress(read)

                        out.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    // Use a closable item and make sure an attempt to close it is done after use
    def useAndClose[T <: {def close() : Unit}](closable: T)(block: T => Unit) {
        try {
            block(closable)
        } finally {
            if (closable ne null)
                runQuietly(closable.close())
        }
    }

    // Run a block and swallow any exception. Use only for things like close().
    def runQuietly(block: => Unit) {
        try {
            block
        } catch {
            case _ => // NOP
        }
    }

    // Implicit conversion from FileItemIterator -> Iterator
    private implicit def asScalaIterator(i : FileItemIterator): Iterator[FileItemStream] = new Iterator[FileItemStream] {
        def hasNext = i.hasNext
        def next = i.next
    }
}