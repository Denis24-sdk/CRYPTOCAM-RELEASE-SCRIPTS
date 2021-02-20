import json
import os
from json.decoder import JSONDecodeError

from ffmpeg import FFmpeg


def create_ffmpeg(file_and_keyfile_path, dest_path, gpg, progress_callback,
                  complete_callback, output_callback, error_callback):
    keyfile_path = file_and_keyfile_path.keyfile_path
    with open(keyfile_path, 'rb') as f:
        result = gpg.decrypt_file(f)
        if not result.ok:
            error_callback(f"GPG error: {result.status}")
            return

        try:
            data = str(result)
            j = json.loads(data)
            timestamp = j['timestamp']
            key = j['encryptionKey']
            out_file_path = os.path.join(dest_path, timestamp + ".mp4")
            ffmpeg = (FFmpeg()
                      .option('y')
                      .option('decryption_key', key)
                      .input(file_and_keyfile_path.file_path)
                      .output(out_file_path))
            ffmpeg.on('progress', progress_callback)
            ffmpeg.on('completed', complete_callback)
            ffmpeg.on('error', lambda code: error_callback(
                f'Error code: {code}'))
            ffmpeg.on('stderr', output_callback)
            return ffmpeg
        except (FileNotFoundError, IOError, JSONDecodeError) as e:
            error_callback(e)
            return None
