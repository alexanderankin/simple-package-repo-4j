/**
 * arch repo looks like
 * <pre>
 *     arch/
 *         $arch/
 *             thing-1-1-$arch.pkg.tar.zst ("$arch" or "any")
 *             thing-1-1-$arch.pkg.tar.zst.sig
 *             $repo.db
 *             $repo.db.tar.gz (same file as previous)
 *             $repo.files (same as db but has ./files as well as ./desc)
 *             $repo.files.tar.gz (same file as previous)
 *             $repo.gpg
 * </pre>
 */
package simple.repo.arch;
