package com.ndmrzzzv.ardiscover

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.rendering.DpToMetersViewSizer
import com.google.ar.sceneform.rendering.ViewAttachmentManager
import com.google.ar.sceneform.rendering.ViewRenderable
import com.ndmrzzzv.ardiscover.data.DataRepository
import com.ndmrzzzv.ardiscover.extension.bearing
import com.ndmrzzzv.ardiscover.extension.degreesToRadians
import com.ndmrzzzv.ardiscover.extension.destination
import com.ndmrzzzv.ardiscover.extension.distance
import com.ndmrzzzv.ardiscover.extension.relativePoint
import com.ndmrzzzv.ardiscover.extension.zeroPoint
import com.ndmrzzzv.ardiscover.filter.KalmanFilter
import com.ndmrzzzv.ardiscover.node.LocationAnchorNode
import com.ndmrzzzv.ardiscover.node.PointOfInterestNode
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.RotationsOrder
import dev.romainguy.kotlin.math.distance
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.addAugmentedImage
import io.github.sceneview.ar.arcore.getUpdatedAugmentedImages
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.math.toRotation
import io.github.sceneview.node.Node
import io.github.sceneview.node.ViewNode
import kotlin.math.atan
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin


class MainFragment : Fragment(R.layout.fragment_main) {

    companion object {
        private val LABEL_DP_PER_METERS = 250
        private fun pixelsToMeters(px: Int, context: Context): Float {
            val density = context.resources.displayMetrics.density
            val dp = px.toFloat() / density
            return dp / LABEL_DP_PER_METERS
        }
    }

    private lateinit var sceneView: ARSceneView
    private lateinit var viewAttachmentManager: ViewAttachmentManager
    private var locationAnchor: LocationAnchorNode? = null

    private val data = DataRepository.get()

    private val augmentedImageNodes = mutableListOf<AugmentedImageNode>()

    private val locationNodes = mutableListOf<PointOfInterestNode>()
    private val screenScaleNodes = mutableListOf<Node>()
    private val cameraFacingNodes = mutableListOf<Node>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sceneView = view.findViewById(R.id.sceneView)
        sceneView.configureSession(this::configureArCoreSession)
        sceneView.onSessionUpdated = this::onSceneUpdateListener
        sceneView.cameraNode.far = 1000f

        viewAttachmentManager = ViewAttachmentManager(requireContext(), sceneView)
    }

    override fun onResume() {
        super.onResume()
        viewAttachmentManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewAttachmentManager.onPause()

        locationAnchor?.kalmanFilter = null
    }

    private fun configureArCoreSession(session: Session, config: Config) {
        for (anchor in data) {
            val bitmap = requireContext()
                .assets
                .open("augmentedimages/${anchor.name}")
                .use(BitmapFactory::decodeStream)

            config.addAugmentedImage(
                session,
                anchor.name,
                bitmap,
                widthInMeters = anchor.physicalWidth.toFloat()
            )
        }
    }

    /**
     * @param nodePosition 3 params:
     * - width (-1 - left, +1 - right) - X
     * - depth (-1 - further, +1 - closer) - Y
     * - height (-1 - lower, +1 - raise) - Z
     */
    private fun onSceneUpdateListener(session: Session, frame: Frame) {
        val detectedImages = frame.getUpdatedAugmentedImages()
        for (detectedImage in detectedImages) {
            val augmentedImageNode =
                augmentedImageNodes.firstOrNull { it.imageName == detectedImage.name }
            if (augmentedImageNode == null) {
                if (detectedImage.trackingState == TrackingState.TRACKING) {
                    for (anchor in data) {

                        if (anchor.name == detectedImage.name) {
                            clearLastLabels()
                            val detectedImageNode =
                                AugmentedImageNode(sceneView.engine, detectedImage)
                            locationAnchor = LocationAnchorNode(detectedImageNode.engine)
                            locationAnchor?.setLocationAnchor(anchor)

                            Toast.makeText(requireContext(), "Detected image!", Toast.LENGTH_LONG)
                                .show()

                            val pointsOfInterest =
                                locationAnchor?.getLocationAnchor()?.pointsOfInterest ?: listOf()

                            for (pointOfInterest in pointsOfInterest) {
                                createLabelNode(pointOfInterest.name) { labelNode ->
                                    val locationNode = PointOfInterestNode(sceneView.engine)
                                    locationNode.setPoint(pointOfInterest)
                                    locationNodes.add(locationNode)

                                    val cameraFacingNode = Node(sceneView.engine)
                                    cameraFacingNodes.add(cameraFacingNode)

                                    val screenScaleNode = Node(sceneView.engine)
                                    screenScaleNodes.add(screenScaleNode)

                                    screenScaleNode.addChildNode(labelNode)
                                    cameraFacingNode.addChildNode(screenScaleNode)
                                    locationNode.addChildNode(cameraFacingNode)
                                    locationAnchor?.addChildNode(locationNode)
                                }
                            }
                            augmentedImageNodes.add(detectedImageNode)
                            locationAnchor?.let {
                                sceneView.addChildNode(detectedImageNode)
                                sceneView.addChildNode(it)

                                updateOrientation(
                                    detectedImageNode,
                                    it,
                                    it.getLocationAnchor().bearing.toFloat()
                                )
                            }
                        }

                    }
                }
            } else {
                locationAnchor?.let {
                    updateOrientation(
                        augmentedImageNode,
                        it,
                        it.getLocationAnchor().bearing.toFloat()
                    )
                }
            }
        }
        updateLocationNodes()
        updateScreenScaleNodes(frame.camera)
        updateCameraFacingNodes()
    }

    private fun createLabelNode(
        text: String,
        onReady: (node: ViewNode) -> Unit
    ) {
        val viewNode =
            ViewNode(sceneView.engine, sceneView.modelLoader, viewAttachmentManager).apply {
                isEditable = false
            }

        val context = sceneView.context
        ViewRenderable
            .builder()
            .setView(context, R.layout.view_sample_text)
            .setSizer(DpToMetersViewSizer(LABEL_DP_PER_METERS))
            .build(sceneView.engine)
            .thenAccept { renderable ->
                val view = renderable.view
                view.findViewById<TextView>(R.id.tv_title).let {
                    it.text = text
                }
                viewNode.setRenderable(renderable)
                onReady(viewNode)

                val viewTreeObserver = view.viewTreeObserver
                if (viewTreeObserver.isAlive) {
                    viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            val width = view.width
                            if (width > 0) {
                                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                val widthInMeters = pixelsToMeters(width, context)
                                viewNode.position = Position(widthInMeters / 2f, 0f, 0f)
                                viewNode.scale = Scale(-1f, 1f, 1f)
                            }
                        }
                    })
                }
            }
    }

    private fun updateLocationNodes() {
        if (locationAnchor != null) {
            val locationAnchorCoordinate =
                locationAnchor?.getLocationAnchor()?.location ?: return
            for (label in locationNodes) {
                val nodeAnchorCoordinate = label.getPoint()
                var relativePoint =
                    locationAnchorCoordinate.coordinate.relativePoint(nodeAnchorCoordinate.location.coordinate)

                val distance = zeroPoint().distance(relativePoint)
                val bearing = zeroPoint().bearing(relativePoint)

                var y = nodeAnchorCoordinate.location.altitude - locationAnchorCoordinate.altitude

                if (distance > 250f) {
                    val maxScaledDistance = 500f
                    val minScaledDistance = 250f
                    val scaleFactor =
                        (maxScaledDistance - minScaledDistance) / log10(distance - minScaledDistance + 1)

                    val squashedDistance =
                        minScaledDistance + log10(distance - minScaledDistance + 1) * scaleFactor

                    relativePoint = zeroPoint().destination(bearing, squashedDistance)

                    val squashFactor = squashedDistance / distance

                    y *= squashFactor.toFloat()
                }

                label.position = Position(
                    x = relativePoint.x,
                    y = y,
                    z = -relativePoint.y
                )
            }
        }
    }

    private fun updateScreenScaleNodes(camera: Camera) {
        val fy = camera.imageIntrinsics.focalLength[1]
        val imageResolution = camera.imageIntrinsics.imageDimensions
        var yFOV = 2 * atan(imageResolution[1] / (2 * fy))

        val visibleYFOVScale = min(
            1f,
            (sceneView.width.toFloat() / sceneView.height.toFloat()) /
                    (imageResolution[1].toFloat() / imageResolution[0].toFloat())
        )

        yFOV *= visibleYFOVScale

        val A = yFOV * 0.5f
        val B = Math.toRadians(180.0) - A - Math.toRadians(90.0)
        val a = (sin(A) * 1) / sin(B)

        val horizontalVisibleDistance = a * 2

        val density = requireContext().resources.displayMetrics.density
        val sceneViewWidthInDp = sceneView.width.toFloat() / density

        val horizontalDistancePerPointAt1m = horizontalVisibleDistance / sceneViewWidthInDp *
                LABEL_DP_PER_METERS

        for (node in screenScaleNodes) {
            val distance = distance(sceneView.cameraNode.worldPosition, node.worldPosition)
            val distancePerPoint = horizontalDistancePerPointAt1m * distance
            node.scale = Scale(distancePerPoint.toFloat())
        }
    }

    private fun updateCameraFacingNodes() {
        val cameraWorldPosition = sceneView.cameraNode.worldPosition
        for (node in cameraFacingNodes) {
            node.lookAt(cameraWorldPosition)
        }
    }

    private fun updateOrientation(imageNode: AugmentedImageNode, anchorNode: Node, bearing: Float) {
        val isMarkerOrientationHorizontal = false

        var angle = 0f
        if (isMarkerOrientationHorizontal) {
            angle = imageNode.transform.toEulerAngles(RotationsOrder.YXZ).y + bearing
            anchorNode.rotation = Quaternion.fromAxisAngle(Float3(0f, 1f, 0f), angle).toRotation()
        } else {
            val rotationMatrix =
                Quaternion.fromAxisAngle(Float3(1f, 0f, 0f), -90f.degreesToRadians().toFloat())
                    .toMatrix()
            val transform = imageNode.transform * rotationMatrix

            angle = transform.toEulerAngles(RotationsOrder.YXZ).y + bearing
        }

        if (locationAnchor?.kalmanFilter != null) {
            locationAnchor?.kalmanFilter?.update(angle, 1f.degreesToRadians().toFloat())
        } else {
            val kalmanFilter = KalmanFilter(angle, 1f.degreesToRadians().toFloat())
            locationAnchor?.kalmanFilter = kalmanFilter
        }

        val angleEstimate = locationAnchor?.kalmanFilter?.getEstimate() ?: return
        anchorNode.rotation =
            Quaternion.fromAxisAngle(Float3(0f, 1f, 0f), angleEstimate).toRotation()
    }

    private fun clearLastLabels() {
        locationNodes.clear()
        cameraFacingNodes.clear()
        screenScaleNodes.clear()
    }
}