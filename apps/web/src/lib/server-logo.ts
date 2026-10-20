const MAX_SOURCE_BYTES = 8 * 1024 * 1024;
const OUTPUT_SIZE = 128;
const MAX_DATA_URL = 120_000;

/** Shrinks an uploaded image into a small PNG or JPEG data URL for a server logo. */
export function readServerLogo(file: File): Promise<string> {
  if (file.type === "image/svg+xml" || !/^image\/(png|jpeg|webp)$/.test(file.type)) {
    return Promise.reject(new Error("Use a PNG, JPEG, or WebP image."));
  }
  if (file.size > MAX_SOURCE_BYTES) {
    return Promise.reject(new Error("That image is too large."));
  }

  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      const canvas = document.createElement("canvas");
      canvas.width = OUTPUT_SIZE;
      canvas.height = OUTPUT_SIZE;
      const ctx = canvas.getContext("2d");
      if (!ctx || image.width < 1 || image.height < 1) {
        reject(new Error("Unable to read that image."));
        return;
      }
      const scale = Math.min(OUTPUT_SIZE / image.width, OUTPUT_SIZE / image.height);
      const width = image.width * scale;
      const height = image.height * scale;
      ctx.drawImage(image, (OUTPUT_SIZE - width) / 2, (OUTPUT_SIZE - height) / 2, width, height);
      const png = canvas.toDataURL("image/png");
      resolve(png.length <= MAX_DATA_URL ? png : canvas.toDataURL("image/jpeg", 0.82));
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("Unable to read that image."));
    };
    image.src = url;
  });
}
