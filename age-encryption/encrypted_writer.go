package cryptocam_age_encryption

// generate bindings and aar with:
// env ANDROID_HOME=... env ANDROID_NDK_HOME=... ~/go/bin/gomobile bind -o ../app/libs/encrypted_writer.aar tnibler.com/cryptocam-age-encryption
import (
	"errors"
	"io"
	"os"
	"strings"

	"filippo.io/age"
)

type EncryptedWriter struct {
    writer *io.WriteCloser
}

func (w EncryptedWriter) Write(data []byte) (int, error) {
    return (*w.writer).Write(data)
}

func (w EncryptedWriter) Close() error {
    return (*w.writer).Close()
}

// recipients is a single string with all recipient public keys separated by newlines
// because arrays aren't supported by gobind
func CreateWriterWithX25519Recipients(fd int, recipients string) (*EncryptedWriter, error) {
    parsed, err := age.ParseRecipients(strings.NewReader(recipients))
    if err != nil {
        return nil, err
    }

    file := os.NewFile(uintptr(fd), "asdasd")
    if file == nil {
		return nil, errors.New("Failed opening file descriptor.")
    }
    encrypted, err := age.Encrypt(file, parsed...)
    if err != nil {
        return nil, err
    }
    return &EncryptedWriter{ &encrypted, }, nil;
}


func CreateWriterWithScryptRecipient(fd int, passphrase string) (*EncryptedWriter, error) {
	recipient, err := age.NewScryptRecipient(passphrase)
	if err != nil {
		return nil, err
	}
	file := os.NewFile(uintptr(fd), "asdasd")
	if file == nil {
		return nil, errors.New("Failed opening file descriptor.")
	}
	encrypted, err := age.Encrypt(file, recipient)
	if err != nil {
		return nil, err
	}
    var encrypted_writer = EncryptedWriter { &encrypted };
	return &encrypted_writer, nil
}

func CheckIsX25519PubKey(key string) bool {
    _, err := age.ParseX25519Recipient(key)
    if err != nil {
        return false
    }
    return true
}
