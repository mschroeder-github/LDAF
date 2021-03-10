package de.dfki.sds.ldaf.resources;

import de.dfki.sds.ldaf.Counter;
import de.dfki.sds.ldaf.LinkedDataResource;
import de.dfki.sds.ldaf.rdf.RDFGraphStorage;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.Part;
import org.apache.commons.io.FileUtils;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.commonjava.mimeparse.MIMEParse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.imgscalr.Scalr;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import spark.Spark;

/**
 * A linked data resource for uploading data.
 */
public class Upload extends LinkedDataResource {

    private File uploadFolder;

    @Override
    public void init() {
        uploadFolder = ldaf.getSettings().getUploadPath();
        uploadFolder.mkdirs();

        Spark.get("/upload", (req, resp) -> this.defaultGetList(req, resp, ldaf.getSettings().getUploadStorage(), ldaf.getSettings().getUploadClass(), "upload.html", null));
        Spark.post("/upload", this::postUpload);
        Spark.get("/upload/:id", this::getUploaded);
        Spark.put("/upload/:id", (req, resp) -> this.defaultPut(req, resp, ldaf.getSettings().getUploadStorage()));
        Spark.patch("/upload/:id", (req, resp) -> this.defaultPatch(req, resp, ldaf.getSettings().getUploadStorage()));
    }

    public String uploading(InputStream is, String submittedFileName) throws IOException {
        return uploading(is, submittedFileName, true);
    }
    
    //to reuse it in other resources
    public String uploading(InputStream is, String submittedFileName, boolean resize) throws IOException {
        
        //pdf special case
        boolean isPDF = submittedFileName.toLowerCase().endsWith(".pdf");
        if(isPDF) {
            int id = counter.getIncreased(Counter.UPLOAD);
            File file = new File(uploadFolder, String.valueOf(id) + "." + "pdf");
            FileUtils.copyInputStreamToFile(is, file);
            return "/" + id;
        }
        
        BufferedImage img = ImageIO.read(is);

        int id = counter.getIncreased(Counter.UPLOAD);

        int w = img.getWidth();
        int h = img.getHeight();
        int max = w > h ? w : h;
        int MAX = 600;

        //resize if highest dimension is > 600
        if (resize && max > MAX) {
            img = Scalr.resize(img, MAX);
        }

        for (String format : Arrays.asList("jpg", "png")) {
            File file = new File(uploadFolder, String.valueOf(id) + "." + format);

            if (format.equals("jpg")) {
                BufferedImage bufimg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                bufimg.createGraphics().drawImage(img, 0, 0, Color.WHITE, null);
                ImageIO.write(bufimg, format, file);
            } else {
                ImageIO.write(img, format, file);
            }
        }

        return "/" + id;
    }

    //upload an image and save on disk and store in graph
    private Object postUpload(Request req, Response resp) {
        //if multipart/form-data
        req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));
        Part part;
        try {
            part = req.raw().getPart("uploaded_file");
        } catch (IOException | ServletException ex) {
            resp.status(HttpStatus.BAD_REQUEST_400);
            System.err.println(ex.getMessage());
            return ex.getMessage();
        }

        try (InputStream is = part.getInputStream()) {

            boolean resize = Boolean.parseBoolean(req.queryParamOrDefault("resize", "true"));
            
            //if from clipboard getSubmittedFileName is "image.png"
            String path = req.uri() + uploading(is, part.getSubmittedFileName(), resize);
            String link = serverAddress + path;

            //create a resoure for it in game graph
            Resource res = ResourceFactory.createResource(link);

            RDFGraphStorage uploadStorage = ldaf.getSettings().getUploadStorage().apply(req);

            uploadStorage.executeWrite(() -> {
                uploadStorage.getModel().add(res, RDF.type, ldaf.getSettings().getUploadClass());
            });

            return response(req, resp, m -> {
                m.put("link", path);
                return "upload.html";
            }, json -> {
                resp.header(HttpHeader.LOCATION.asString(), path);
            }, model -> {
                model.add(res, RDF.type, ldaf.getSettings().getUploadClass());
            });

        } catch (IOException ex) {
            return error(ex, resp);
        }
    }

    private String error(Exception ex, Response resp) {
        resp.status(HttpStatus.BAD_REQUEST_400);
        System.err.println(ex.getMessage());
        return ex.getMessage();
    }

    //returns the image bytes or json/rdf
    private Object getUploaded(Request req, Response resp) throws IOException {
        String accept = req.headers(HttpHeader.ACCEPT.asString());

        List<String> supported = new ArrayList<>();

        supported.add("text/turtle");
        supported.add("application/json");
        supported.add("text/html");

        String match = MIMEParse.bestMatch(supported, accept);

        Resource res = ResourceFactory.createResource(address(req));

        switch (match) {

            //return image
            case "text/html": {
                String id = req.params("id");

                //ensure ending
                if (!id.endsWith(".jpg") && !id.endsWith(".png") && !id.endsWith(".pdf")) {
                    for(String ext : Arrays.asList("pdf", "jpg", "png")) {
                        String tmpName = id + "." + ext;
                        File tmpFile = new File(uploadFolder, tmpName);
                        if(tmpFile.exists()) {
                            id = tmpName;
                            break;
                        }
                    }
                }

                File imageFile = new File(uploadFolder, id);

                if (!imageFile.exists()) {
                    resp.status(HttpStatus.NOT_FOUND_404);
                    return "Image not found";
                }

                if (id.endsWith(".jpg")) {
                    resp.type("image/jpeg");
                } else if (id.endsWith(".png")) {
                    resp.type("image/png");
                } else if (id.endsWith(".pdf")) {
                    resp.type("application/pdf");
                }

                /*
                boolean isThumbnail = req.queryParams("w") != null || req.queryParams("h") != null;
                if (isThumbnail) {
                    //long begin = System.currentTimeMillis();
                    
                    Dimension boundary = new Dimension();
                    if(req.queryParams("w") != null) {
                        int w = Integer.parseInt(req.queryParams("w"));
                        boundary.width = w;
                        boundary.height = w;
                    } else if(req.queryParams("h") != null) {
                        int h = Integer.parseInt(req.queryParams("h"));
                        boundary.width = h;
                        boundary.height = h;
                    }
                    
                    BufferedImage img = ImageIO.read(imageFile);
                    Dimension scaled = getScaledDimension(new Dimension(img.getWidth(), img.getHeight()), boundary);
                    BufferedImage thumbnail = new BufferedImage(scaled.width, scaled.height, BufferedImage.TYPE_3BYTE_BGR); //img.getType());
                    Graphics g = thumbnail.getGraphics();
                    ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(img, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), null);
                    g.dispose();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(thumbnail, "jpg", baos);
                    byte[] array = baos.toByteArray();
                    
                    //long end = System.currentTimeMillis();
                    
                    resp.type("image/jpeg");
                    return array;
                }
                 */
                return FileUtils.readFileToByteArray(imageFile);
            }
            case "application/json": {
                resp.type("application/json");

                RDFGraphStorage uploadStorage = ldaf.getSettings().getUploadStorage().apply(req);

                JSONObject obj = uploadStorage.calculateRead(() -> {
                    boolean contains = uploadStorage.getModel().containsResource(res);
                    if (!contains) {
                        return null;
                    }

                    //get model about subject
                    return converter.toJSON(res, uploadStorage.getModel());
                });

                //if model is null there is no resource found
                if (obj == null) {
                    Spark.halt(HttpStatus.NOT_FOUND_404, "resource not found");
                }

                return obj.toString(2);
            }
            case "text/turtle": {
                //TODO upload component rdf impl
                /*
                resp.type("text/turtle");
                Model model = modelAbout(req, res);
                StringWriter sw = new StringWriter();
                model.write(sw, "TTL");
                return sw.toString();
                 */
                return "TODO";
            }
            case "": {
                resp.status(HttpStatus.NOT_ACCEPTABLE_406);
                return "";
            }
        }
        return "";
    }

}
