import SwiftUI
import UIKit

/// Системная камера. На симуляторе недоступна — используйте выбор из галереи.
struct CameraPicker: UIViewControllerRepresentable {
    var onImage: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController()
        p.sourceType = .camera
        p.delegate = context.coordinator
        return p
    }

    func updateUIViewController(_ vc: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let img = info[.originalImage] as? UIImage { parent.onImage(img.normalizedOrientation()) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}

extension UIImage {
    /// Поворот по EXIF и уменьшение до maxSide пикселей. Масштаб 1: иначе рендер идёт в 3× от размера
    /// (фото 12 Мп превращалось в ~110 Мп и сотни мегабайт памяти). Прозрачность — на белом фоне.
    func prepared(maxSide: CGFloat = 2400) -> UIImage {
        let px = CGSize(width: size.width * scale, height: size.height * scale)
        let k = min(1, maxSide / max(px.width, px.height, 1))
        if imageOrientation == .up && k >= 1 && scale == 1 { return self }
        let target = CGSize(width: max(1, (px.width * k).rounded()), height: max(1, (px.height * k).rounded()))
        let f = UIGraphicsImageRendererFormat()
        f.scale = 1
        f.opaque = true
        return UIGraphicsImageRenderer(size: target, format: f).image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: target))
            draw(in: CGRect(origin: .zero, size: target))
        }
    }

    /// Чертёж: ровно и не больше 2400 px — хватает для ИИ и чтения, не забивает память.
    func normalizedOrientation() -> UIImage { prepared() }

    /// Картинка для PDF: до maxSide px и в JPEG, чтобы файл КП был лёгким для WhatsApp и почты.
    func forPDF(maxSide: CGFloat = 1400) -> UIImage {
        let img = prepared(maxSide: maxSide)
        return img.jpegData(compressionQuality: 0.8).flatMap(UIImage.init(data:)) ?? img
    }
}
