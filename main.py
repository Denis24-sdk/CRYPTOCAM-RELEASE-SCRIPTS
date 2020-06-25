import os
import sys
import gnupg
import json
import ffmpeg
# except ImportError:
#     print('No ffmpeg installation found. Please make sure ffmpeg is installed.')
#     exit(1)
import argparse

def decrypt_video(file_in, key_file, gpg, path_out):
    with open(key_file, 'rb') as f:
        decrypted_data = gpg.decrypt_file(f)
        if (not decrypted_data.ok):
            print(f'Failed to decrypt: {key_file}: {decrypted_data.status}. Do you have the right key in your keyring?')
            return False
        else:
            json_data = json.loads(str(decrypted_data))
            key = json_data['encryptionKey']
            out_name = json_data['timestamp']
            try:
                (
                ffmpeg.input(file_in, decryption_key=key)
                .output(os.path.join(path_out, out_name + ".mp4"))
                .run()
                )
            except:
                return False
            return True

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('files', nargs='+', help='videos and keyfiles to be decrypted')
    parser.add_argument('--destination', '-d', help='location where decrypted videos should be output', default='.')
    parser.add_argument('--gpg-home', help='GnuPG gome location', default=None)
    args = parser.parse_args()
    gpg = gnupg.GPG(gnupghome=args.gpg_home)
    gpg.encoding = 'utf-8'
    key_files = [fn for fn in args.files if fn.endswith('.pgp')]
    total_decrypted = 0
    for key_file in key_files:
        success = decrypt_video(key_file[:-4] + '.mp4', key_file, gpg, args.destination)
        total_decrypted += 1 if success else 0
    print(f'Decrypted {total_decrypted} videos.')
