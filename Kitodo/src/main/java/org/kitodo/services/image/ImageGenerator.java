/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.services.image;

import java.awt.Image;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.filemanagement.FileManagementInterface;
import org.kitodo.api.imagemanagement.ImageFileFormat;
import org.kitodo.api.imagemanagement.ImageManagementInterface;
import org.kitodo.config.xml.fileformats.FileFormat;
import org.kitodo.data.database.beans.Folder;
import org.kitodo.helper.tasks.EmptyTask;
import org.kitodo.model.UseFolder;
import org.kitodo.production.thread.TaskScriptThread;
import org.kitodo.serviceloader.KitodoServiceLoader;

/**
 * A program that generates images using the image management interface. This
 * program is run by the {@link ImageGeneratorTask} when the user manually
 * initiates the creation of the images. If the images are generated when the
 * task is completed, this is done by the {@link TaskScriptThread}.
 */
public class ImageGenerator implements Runnable {
    private static final Logger logger = LogManager.getLogger(ImageGenerator.class);

    /**
     * Output folders.
     */
    private Collection<UseFolder> outputs;

    /**
     * Current position in list.
     */
    private int position;

    /**
     * Folder with source images.
     */
    private UseFolder sourceFolder;

    /**
     * List of possible source images.
     */
    private List<Pair<String, URI>> sources;

    /**
     * Current step of the generation process.
     */
    private ImageGeneratorStep state;

    /**
     * Task in the TaskManager that runs this ImageGenerator.
     */
    private Optional<EmptyTask> supervisor = Optional.empty();

    /**
     * List of elements to be generated.
     */
    private List<Pair<Pair<String, URI>, List<UseFolder>>> toBeGenerated;

    /**
     * Variant of image generation, see there.
     */
    private GenerationMode variant;

    /**
     * Creates a new image generator.
     *
     * @param sourceFolder
     *            image source folder
     * @param variant
     *            variant of image generation
     * @param outputs
     *            output folders to generate to
     */
    public ImageGenerator(UseFolder sourceFolder, GenerationMode variant, Collection<UseFolder> outputs) {
        this.sourceFolder = sourceFolder;
        this.variant = variant;
        this.outputs = outputs;
        this.state = ImageGeneratorStep.LIST_SOURCE_FOLDER;
        this.sources = Collections.emptyList();
        this.toBeGenerated = new LinkedList<>();
    }

    /**
     * Appends the element to the list of elements to be generated.
     *
     * @param elementToAppend
     *            element to be appended to the list
     */
    void addToToBeGenerated(Pair<Pair<String, URI>, List<UseFolder>> elementToAppend) {
        toBeGenerated.add(elementToAppend);
    }

    /**
     * Invokes the image management interface method that creates a derivative.
     * What kind of derivative should be created is determined from the folder
     * configuration.
     *
     * @param sourceImage
     *            address of the source image from which the derivative is to be
     *            calculated.
     * @param imageProperties
     *            configuration for the target image
     * @param fileFormat
     *            image file format to create
     * @param destinationImage
     *            image file to write
     * @throws IOException
     *             if an underlying disk operation fails
     */
    private static void createDerivative(URI sourceImage, Folder imageProperties, ImageFileFormat imageFileFormat,
            URI destinationImage) throws IOException {

        ImageManagementInterface imageGenerator 
            = new KitodoServiceLoader<ImageManagementInterface>(ImageManagementInterface.class).loadModule();
        imageGenerator.createDerivative(sourceImage, imageProperties.getDerivative().get(), destinationImage,
            imageFileFormat);
    }

    /**
     * Generates a set of derivatives.
     *
     * @param instruction
     *            Instruction, which pictures are to be generated. Left: image
     *            source, right: destination folder. The image source consists
     *            of the canonical part of the file name and the resolved file
     *            name for the source image. The canonical part of the file name
     *            is needed to calculate the corresponding file name in the
     *            destination folder. The type of derivative to be generated is
     *            defined in the properties of the destination folder.
     */
    void createDerivatives(Pair<Pair<String, URI>, List<UseFolder>> instruction) {
        try {
            Pair<String, URI> imageSource = instruction.getLeft();
            for (UseFolder destinationFolder : instruction.getRight()) {
                generateDerivative(imageSource.getValue(), destinationFolder, imageSource.getKey());
            }
        } catch (IOException e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    /**
     * Generates a derived image and saves it with the on-board tools of Java.
     * The image is created by the image management interface. Which method of
     * the interface is called and its parameters are determined in the
     * configuration of the folder. The same is true for the file type under
     * which Java stores the image.
     *
     * @param sourceImage
     *            reference to the image that serves as a template for the
     *            reproduction process
     * @param imageProperties
     *            folder settings define what an image is created
     * @param fileFormat
     *            the file format specifies how the image should be saved
     * @param destinationImage
     *            specifies the location where the image should be written
     * @throws IOException
     *             if an underlying disk operation fails
     */
    private static void createImageWithImageIO(URI sourceImage, Folder imageProperties, FileFormat fileFormat,
            URI destinationImage) throws IOException {

        try (OutputStream outputStream = new KitodoServiceLoader<FileManagementInterface>(FileManagementInterface.class)
                .loadModule().write(destinationImage)) {
            Image image = retrieveJavaImage(sourceImage, imageProperties);
            ImageIO.write((RenderedImage) image, fileFormat.getFormatName().get(), outputStream);
        }
    }

    /**
     * Determines the folders in which a derivative must be created. Because the
     * ModuleLoader does not work when invoked from a parallelStream(), we use a
     * classic loop here.
     *
     * @param canonical
     *            canonical part of the file name, to determine the file names
     *            in the destination folder
     * @return the images to be generated
     */
    List<UseFolder> determineFoldersThatNeedDerivatives(String canonical) {
        List<UseFolder> result = new ArrayList<>(outputs.size());
        Predicate<? super UseFolder> requiresGeneration = variant.getFilter(canonical);
        for (UseFolder folder : outputs) {
            if (requiresGeneration.test(folder)) {
                result.add(folder);
            }
        }
        return result;
    }

    /**
     * Gets the file list from the content folder, converts it into the required
     * form, and stores it in the sources field.
     */
    void determineSources() {
        Map<String, URI> contents = sourceFolder.listContents();
        Stream<Entry<String, URI>> contentsStream = contents.entrySet().stream();
        Stream<Pair<String, URI>> sourcesStream = contentsStream.map(λ -> Pair.of(λ.getKey(), λ.getValue()));
        this.sources = sourcesStream.collect(Collectors.toList());
    }

    /**
     * Generates the derivative depending on the declared generator function.
     *
     * @param sourceImage
     *            source file
     * @param imageProperties
     *            folder whose configuration specifies what should be created
     * @param fileFormat
     *            the file format to be created
     * @param destinationImage
     *            path to the target file to be generated
     * @throws IOException
     *             if filesystem I/O fails
     */
    private static void generateDerivative(URI sourceImage, UseFolder destinationUse, String canonical)
            throws IOException {

        Folder imageProperties = destinationUse.getFolder();
        boolean isCreatingDerivative = imageProperties.getDerivative().isPresent();
        boolean isChangingDpi = imageProperties.getDpi().isPresent();
        boolean isGettingScaledWebImage = imageProperties.getImageScale().isPresent();
        boolean isGettingSizedWebImage = imageProperties.getImageSize().isPresent();

        if (isCreatingDerivative) {
            createDerivative(sourceImage, imageProperties, destinationUse.getFileFormat().getImageFileFormat().get(),
                destinationUse.getUri(canonical));
        } else if (isChangingDpi || isGettingScaledWebImage || isGettingSizedWebImage) {
            createImageWithImageIO(sourceImage, imageProperties, destinationUse.getFileFormat(),
                destinationUse.getUri(canonical));
        }
    }

    /**
     * Returns from toBeGenerated the item specified by position.
     *
     * @return the item specified by position
     */
    Pair<Pair<String, URI>, List<UseFolder>> getFromToBeGeneratedByPosition() {
        return toBeGenerated.get(position);
    }

    /**
     * Returns the current position in the list.
     *
     * @return the position
     */
    int getPosition() {
        return position;
    }

    /**
     * Returns the list of source images.
     *
     * @return the list of source images
     */
    List<Pair<String, URI>> getSources() {
        return sources;
    }

    /**
     * Returns the list of image generation task descriptions.
     *
     * @return the list of image generation task descriptions
     */
    List<Pair<Pair<String, URI>, List<UseFolder>>> getToBeGenerated() {
        return toBeGenerated;
    }

    /**
     * Returns the enum constant inicating the variant of the image generator
     * task.
     *
     * @return the variant of the image generator task
     */
    GenerationMode getVariant() {
        return variant;
    }

    /**
     * Lets the supervisor do something, if there is one. Otherwise nothing
     * happens.
     *
     * @param action
     *            what the supervisor should do
     */
    void letTheSupervisor(Consumer<EmptyTask> action) {
        if (supervisor.isPresent()) {
            action.accept(supervisor.get());
        }

    }

    /**
     * Invokes one of the three methods of the image management interface that
     * return a Java image. Which method is called and its parameters are
     * determined in the configuration of the folder.
     *
     * @param sourceImage
     *            address of the source image from which the derivative is to be
     *            calculated.
     * @param imageProperties
     *            configuration for the target image
     * @return an image in memory
     * @throws IOException
     *             if an underlying disk operation fails
     */
    private static Image retrieveJavaImage(URI sourceImage, Folder imageProperties) throws IOException {

        ImageManagementInterface imageManagementServiceProvider = new KitodoServiceLoader<ImageManagementInterface>(
                ImageManagementInterface.class).loadModule();

        if (imageProperties.getDpi().isPresent()) {
            return imageManagementServiceProvider.changeDpi(sourceImage, imageProperties.getDpi().get());
        } else if (imageProperties.getImageScale().isPresent()) {
            return imageManagementServiceProvider.getScaledWebImage(sourceImage, imageProperties.getImageScale().get());
        } else if (imageProperties.getImageSize().isPresent()) {
            return imageManagementServiceProvider.getSizedWebImage(sourceImage, imageProperties.getImageSize().get());
        }
        throw new IllegalArgumentException(imageProperties + " does not give any method to create a java image");
    }

    /**
     * If the task is started, it will execute this run() method which will
     * start the export on the ExportDms. This task instance is passed in
     * addition so that the ExportDms can update the task’s state.
     *
     * @see de.sub.goobi.helper.tasks.EmptyTask#run()
     */
    @Override
    public void run() {
        do {
            state.accept(this);
            setPosition(getPosition() + 1);
            setProgress();
            if (supervisor.isPresent() && supervisor.get().isInterrupted()) {
                return;
            }
        } while (!(state.equals(ImageGeneratorStep.GENERATE_IMAGES) && getPosition() == getToBeGenerated().size()));
        logger.info("Completed");
    }

    /**
     * Sets the current position in the list.
     *
     * @param position
     *            position to set
     */
    void setPosition(int position) {
        this.position = position;
    }

    /**
     * Calculates and reports the progress of the task.
     */
    private void setProgress() {
        if (supervisor.isPresent()) {
            int checked = state.equals(ImageGeneratorStep.GENERATE_IMAGES)
                    ? getVariant().equals(GenerationMode.ALL) ? 1 : sources.size()
                    : 0;
            int generated = getVariant().equals(GenerationMode.ALL)
                    && state.equals(ImageGeneratorStep.DETERMINE_WHICH_IMAGES_NEED_TO_BE_GENERATED) ? 0 : getPosition();
            int total = sources.size() + (getVariant().equals(GenerationMode.ALL) ? 1 : getToBeGenerated().size()) + 1;
            supervisor.get().setProgress(100d * (1 + checked + generated) / total);
        }
    }

    /**
     * Sets the current processing state.
     *
     * @param state
     *            state to set
     */
    void setState(ImageGeneratorStep state) {
        this.state = state;
    }

    /**
     * Set a supervisor for this activity. If a supervisor is set, the progress
     * is reported back to him, and he responds to his interrupt requests.
     *
     * @param supervisor
     *            supervisor task to set
     */
    public void setSupervisor(EmptyTask supervisor) {
        this.supervisor = Optional.of(supervisor);
    }
}
