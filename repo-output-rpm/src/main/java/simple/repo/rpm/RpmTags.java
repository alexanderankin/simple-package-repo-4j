package simple.repo.rpm;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public sealed interface RpmTags {

    int getTagValue();

    @Getter
    @RequiredArgsConstructor
    enum HeaderTag implements RpmTags {
        HEADER_IMAGE(61),
        HEADER_SIGNATURES(62),
        HEADER_IMMUTABLE(63),
        HEADER_REGIONS(64),
        HEADER_I18NTABLE(100),
        HEADER_SIGBASE(256),
        HEADER_SIGTOP(999),
        HEADER_TAGBASE(1000),
        ;
        private final int tagValue;
    }

    @Getter
    @RequiredArgsConstructor
    enum RpmTag implements RpmTags {
        // @formatter:off
        RPMTAG_HEADERIMAGE                (HeaderTag.HEADER_IMAGE.tagValue),      /*!< Current image. */
        RPMTAG_HEADERSIGNATURES           (HeaderTag.HEADER_SIGNATURES.tagValue), /*!< Signatures. */
        RPMTAG_HEADERIMMUTABLE            (HeaderTag.HEADER_IMMUTABLE.tagValue, "x"),  /* x Original image. */
        RPMTAG_HEADERREGIONS              (HeaderTag.HEADER_REGIONS.tagValue),    /*!< Regions. */
        RPMTAG_HEADERI18NTABLE            (HeaderTag.HEADER_I18NTABLE.tagValue, "s[]"),  /* s[] !< I18N string locales. */
        RPMTAG_SIG_BASE                   (HeaderTag.HEADER_SIGBASE.tagValue),
        RPMTAG_SIGSIZE                    (RPMTAG_SIG_BASE.tagValue+1, "i"),  /* i */
        RPMTAG_SIGLEMD5_1                 (RPMTAG_SIG_BASE.tagValue+2),  /* internal - obsolete */
        RPMTAG_SIGPGP                     (RPMTAG_SIG_BASE.tagValue+3, "x"),  /* x */
        RPMTAG_SIGLEMD5_2                 (RPMTAG_SIG_BASE.tagValue+4, "x"),  /* x internal - obsolete */
        RPMTAG_SIGMD5                     (RPMTAG_SIG_BASE.tagValue+5, "x"),  /* x */
        RPMTAG_SIGGPG                     (RPMTAG_SIG_BASE.tagValue+6, "x"),  /* x */
        RPMTAG_SIGPGP5                    (RPMTAG_SIG_BASE.tagValue+7),  /* internal - obsolete */
        RPMTAG_BADSHA1_1                  (RPMTAG_SIG_BASE.tagValue+8),  /* internal - obsolete */
        RPMTAG_BADSHA1_2                  (RPMTAG_SIG_BASE.tagValue+9),  /* internal - obsolete */
        RPMTAG_PUBKEYS                    (RPMTAG_SIG_BASE.tagValue+10, "s[]"), /* s[] */
        RPMTAG_DSAHEADER                  (RPMTAG_SIG_BASE.tagValue+11, "x"), /* x */
        RPMTAG_RSAHEADER                  (RPMTAG_SIG_BASE.tagValue+12, "x"), /* x */
        RPMTAG_SHA1HEADER                 (RPMTAG_SIG_BASE.tagValue+13, "s"), /* s */
        RPMTAG_LONGSIGSIZE                (RPMTAG_SIG_BASE.tagValue+14, "l"), /* l */
        RPMTAG_LONGARCHIVESIZE            (RPMTAG_SIG_BASE.tagValue+15, "l"), /* l */
        RPMTAG_SHA256HEADER               (RPMTAG_SIG_BASE.tagValue+17, "s"), /* s */
        RPMTAG_VERITYSIGNATURES           (RPMTAG_SIG_BASE.tagValue+20, "s[]"), /* s[] */
        RPMTAG_VERITYSIGNATUREALGO        (RPMTAG_SIG_BASE.tagValue+21, "i"), /* i */
        RPMTAG_OPENPGP                    (RPMTAG_SIG_BASE.tagValue+22, "s[]"), /* s[] */
        RPMTAG_SHA3_256HEADER             (RPMTAG_SIG_BASE.tagValue+23, "s"), /* s */
        RPMTAG_SIG_TOP                    (HeaderTag.HEADER_SIGTOP.tagValue),
        RPMTAG_NAME                       (1000, "s"),                   /* s */
        RPMTAG_N                          (RPMTAG_NAME, "s"),            /* s */
        RPMTAG_VERSION                    (1001, "s"),                   /* s */
        RPMTAG_V                          (RPMTAG_VERSION, "s"),         /* s */
        RPMTAG_RELEASE                    (1002, "s"),                   /* s */
        RPMTAG_R                          (RPMTAG_RELEASE, "s"),         /* s */
        RPMTAG_EPOCH                      (1003, "i"),                   /* i */
        RPMTAG_E                          (RPMTAG_EPOCH, "i"),           /* i */
        RPMTAG_SUMMARY                    (1004, "s{}"),                   /* s{} */
        RPMTAG_DESCRIPTION                (1005, "s{}"),                   /* s{} */
        RPMTAG_BUILDTIME                  (1006, "i"),                   /* i */
        RPMTAG_BUILDHOST                  (1007, "s"),                   /* s */
        RPMTAG_INSTALLTIME                (1008, "i"),                   /* i */
        RPMTAG_SIZE                       (1009, "i"),                   /* i */
        RPMTAG_DISTRIBUTION               (1010, "s"),                   /* s */
        RPMTAG_VENDOR                     (1011, "s"),                   /* s */
        RPMTAG_GIF                        (1012, "x"),                   /* x */
        RPMTAG_XPM                        (1013, "x"),                   /* x */
        RPMTAG_LICENSE                    (1014, "s"),                   /* s */
        RPMTAG_PACKAGER                   (1015, "s"),                   /* s */
        RPMTAG_GROUP                      (1016, "s{}"),                   /* s{} */
        RPMTAG_CHANGELOG                  (1017, "s[]"),                   /* s[] internal */
        RPMTAG_SOURCE                     (1018, "s[]"),                   /* s[] */
        RPMTAG_PATCH                      (1019, "s[]"),                   /* s[] */
        RPMTAG_URL                        (1020, "s"),                   /* s */
        RPMTAG_OS                         (1021, "s"),                   /* s legacy used int */
        RPMTAG_ARCH                       (1022, "s"),                   /* s legacy used int */
        RPMTAG_PREIN                      (1023, "s"),                   /* s */
        RPMTAG_POSTIN                     (1024, "s"),                   /* s */
        RPMTAG_PREUN                      (1025, "s"),                   /* s */
        RPMTAG_POSTUN                     (1026, "s"),                   /* s */
        RPMTAG_OLDFILENAMES               (1027, "s[]"),                   /* s[] obsolete */
        RPMTAG_FILESIZES                  (1028, "i[]"),                   /* i[] */
        RPMTAG_FILESTATES                 (1029, "c[]"),                   /* c[] */
        RPMTAG_FILEMODES                  (1030, "h[]"),                   /* h[] */
        RPMTAG_FILEUIDS                   (1031, "i[]"),                   /* i[] internal - obsolete */
        RPMTAG_FILEGIDS                   (1032, "i[]"),                   /* i[] internal - obsolete */
        RPMTAG_FILERDEVS                  (1033, "h[]"),                   /* h[] */
        RPMTAG_FILEMTIMES                 (1034, "i[]"),                   /* i[] */
        RPMTAG_FILEDIGESTS                (1035, "s[]"),                   /* s[] */
        RPMTAG_FILEMD5S                   (RPMTAG_FILEDIGESTS, "s[]"),     /* s[] */
        RPMTAG_FILELINKTOS                (1036, "s[]"),                   /* s[] */
        RPMTAG_FILEFLAGS                  (1037, "i[]"),                   /* i[] */
        RPMTAG_ROOT                       (1038),                   /* internal - obsolete */
        RPMTAG_FILEUSERNAME               (1039, "s[]"),                   /* s[] */
        RPMTAG_FILEGROUPNAME              (1040, "s[]"),                   /* s[] */
        RPMTAG_EXCLUDE                    (1041),                   /* internal - obsolete */
        RPMTAG_EXCLUSIVE                  (1042),                   /* internal - obsolete */
        RPMTAG_ICON                       (1043, "x"),                   /* x */
        RPMTAG_SOURCERPM                  (1044, "s"),                   /* s */
        RPMTAG_FILEVERIFYFLAGS            (1045, "i[]"),                   /* i[] */
        RPMTAG_ARCHIVESIZE                (1046, "i"),                   /* i */
        RPMTAG_PROVIDENAME                (1047, "s[]"),                   /* s[] */
        RPMTAG_PROVIDES                   (RPMTAG_PROVIDENAME, "s[]"),     /* s[] */
        RPMTAG_P                          (RPMTAG_PROVIDENAME, "s[]"),     /* s[] */
        RPMTAG_REQUIREFLAGS               (1048, "i[]"),                   /* i[] */
        RPMTAG_REQUIRENAME                (1049, "s[]"),                   /* s[] */
        RPMTAG_REQUIRES                   (RPMTAG_REQUIRENAME, "s[]"),     /* s[] */
        RPMTAG_REQUIREVERSION             (1050, "s[]"),                   /* s[] */
        RPMTAG_NOSOURCE                   (1051, "i[]"),                   /* i[] */
        RPMTAG_NOPATCH                    (1052, "i[]"),                   /* i[] */
        RPMTAG_CONFLICTFLAGS              (1053, "i[]"),                   /* i[] */
        RPMTAG_CONFLICTNAME               (1054, "s[]"),                   /* s[] */
        RPMTAG_CONFLICTS                  (RPMTAG_CONFLICTNAME, "s[]"),    /* s[] */
        RPMTAG_C                          (RPMTAG_CONFLICTNAME, "s[]"),    /* s[] */
        RPMTAG_CONFLICTVERSION            (1055, "s[]"),                   /* s[] */
        RPMTAG_DEFAULTPREFIX              (1056, "s"),                   /* s internal - deprecated */
        RPMTAG_BUILDROOT                  (1057, "s"),                   /* s internal - obsolete */
        RPMTAG_INSTALLPREFIX              (1058, "s"),                   /* s internal - deprecated */
        RPMTAG_EXCLUDEARCH                (1059, "s[]"),                   /* s[] */
        RPMTAG_EXCLUDEOS                  (1060, "s[]"),                   /* s[] */
        RPMTAG_EXCLUSIVEARCH              (1061, "s[]"),                   /* s[] */
        RPMTAG_EXCLUSIVEOS                (1062, "s[]"),                   /* s[] */
        RPMTAG_AUTOREQPROV                (1063, "s"),                   /* s internal */
        RPMTAG_RPMVERSION                 (1064, "s"),                   /* s */
        RPMTAG_TRIGGERSCRIPTS             (1065, "s[]"),                   /* s[] */
        RPMTAG_TRIGGERNAME                (1066, "s[]"),                   /* s[] */
        RPMTAG_TRIGGERVERSION             (1067, "s[]"),                   /* s[] */
        RPMTAG_TRIGGERFLAGS               (1068, "i[]"),                   /* i[] */
        RPMTAG_TRIGGERINDEX               (1069, "i[]"),                   /* i[] */
        RPMTAG_VERIFYSCRIPT               (1079, "s"),                   /* s */
        RPMTAG_CHANGELOGTIME              (1080, "i[]"),                   /* i[] */
        RPMTAG_CHANGELOGNAME              (1081, "s[]"),                   /* s[] */
        RPMTAG_CHANGELOGTEXT              (1082, "s[]"),                   /* s[] */
        RPMTAG_BROKENMD5                  (1083),                   /* internal - obsolete */
        RPMTAG_PREREQ                     (1084),                   /* internal */
        RPMTAG_PREINPROG                  (1085, "s[]"),                   /* s[] */
        RPMTAG_POSTINPROG                 (1086, "s[]"),                   /* s[] */
        RPMTAG_PREUNPROG                  (1087, "s[]"),                   /* s[] */
        RPMTAG_POSTUNPROG                 (1088, "s[]"),                   /* s[] */
        RPMTAG_BUILDARCHS                 (1089, "s[]"),                   /* s[] */
        RPMTAG_OBSOLETENAME               (1090, "s[]"),                   /* s[] */
        RPMTAG_OBSOLETES                  (RPMTAG_OBSOLETENAME, "s[]"),    /* s[] */
        RPMTAG_O                          (RPMTAG_OBSOLETENAME, "s[]"),    /* s[] */
        RPMTAG_VERIFYSCRIPTPROG           (1091, "s[]"),                   /* s[] */
        RPMTAG_TRIGGERSCRIPTPROG          (1092, "s[]"),                   /* s[] */
        RPMTAG_DOCDIR                     (1093),                   /* internal */
        RPMTAG_COOKIE                     (1094, "s"),                   /* s */
        RPMTAG_FILEDEVICES                (1095, "i[]"),                   /* i[] */
        RPMTAG_FILEINODES                 (1096, "i[]"),                   /* i[] */
        RPMTAG_FILELANGS                  (1097, "s[]"),                   /* s[] */
        RPMTAG_PREFIXES                   (1098, "s[]"),                   /* s[] */
        RPMTAG_INSTPREFIXES               (1099, "s[]"),                   /* s[] */
        RPMTAG_TRIGGERIN                  (1100),                   /* internal */
        RPMTAG_TRIGGERUN                  (1101),                   /* internal */
        RPMTAG_TRIGGERPOSTUN              (1102),                   /* internal */
        RPMTAG_AUTOREQ                    (1103),                   /* internal */
        RPMTAG_AUTOPROV                   (1104),                   /* internal */
        RPMTAG_CAPABILITY                 (1105, "i"),                   /* i internal - obsolete */
        RPMTAG_SOURCEPACKAGE              (1106, "i"),                   /* i */
        RPMTAG_OLDORIGFILENAMES           (1107),                   /* internal - obsolete */
        RPMTAG_BUILDPREREQ                (1108),                   /* internal */
        RPMTAG_BUILDREQUIRES              (1109),                   /* internal */
        RPMTAG_BUILDCONFLICTS             (1110),                   /* internal */
        RPMTAG_BUILDMACROS                (1111),                   /* internal - unused */
        RPMTAG_PROVIDEFLAGS               (1112, "i[]"),                   /* i[] */
        RPMTAG_PROVIDEVERSION             (1113, "s[]"),                   /* s[] */
        RPMTAG_OBSOLETEFLAGS              (1114, "i[]"),                   /* i[] */
        RPMTAG_OBSOLETEVERSION            (1115, "s[]"),                   /* s[] */
        RPMTAG_DIRINDEXES                 (1116, "i[]"),                   /* i[] */
        RPMTAG_BASENAMES                  (1117, "s[]"),                   /* s[] */
        RPMTAG_DIRNAMES                   (1118, "s[]"),                   /* s[] */
        RPMTAG_ORIGDIRINDEXES             (1119, "i[]"),                   /* i[] relocation */
        RPMTAG_ORIGBASENAMES              (1120, "s[]"),                   /* s[] relocation */
        RPMTAG_ORIGDIRNAMES               (1121, "s[]"),                   /* s[] relocation */
        RPMTAG_OPTFLAGS                   (1122, "s"),                   /* s */
        RPMTAG_DISTURL                    (1123, "s"),                   /* s */
        RPMTAG_PAYLOADFORMAT              (1124, "s"),                   /* s */
        RPMTAG_PAYLOADCOMPRESSOR          (1125, "s"),                   /* s */
        RPMTAG_PAYLOADFLAGS               (1126, "s"),                   /* s */
        RPMTAG_INSTALLCOLOR               (1127, "i"),                   /* i transaction color when installed */
        RPMTAG_INSTALLTID                 (1128, "i"),                   /* i */
        RPMTAG_REMOVETID                  (1129, "i"),                   /* i */
        RPMTAG_SHA1RHN                    (1130),                   /* internal - obsolete */
        RPMTAG_RHNPLATFORM                (1131, "s"),                   /* s internal - obsolete */
        RPMTAG_PLATFORM                   (1132, "s"),                   /* s */
        RPMTAG_PATCHESNAME                (1133, "s[]"),                   /* s[] deprecated placeholder (SuSE) */
        RPMTAG_PATCHESFLAGS               (1134, "i[]"),                   /* i[] deprecated placeholder (SuSE) */
        RPMTAG_PATCHESVERSION             (1135, "s[]"),                   /* s[] deprecated placeholder (SuSE) */
        RPMTAG_CACHECTIME                 (1136, "i"),                   /* i internal - obsolete */
        RPMTAG_CACHEPKGPATH               (1137, "s"),                   /* s internal - obsolete */
        RPMTAG_CACHEPKGSIZE               (1138, "i"),                   /* i internal - obsolete */
        RPMTAG_CACHEPKGMTIME              (1139, "i"),                   /* i internal - obsolete */
        RPMTAG_FILECOLORS                 (1140, "i[]"),                   /* i[] */
        RPMTAG_FILECLASS                  (1141, "i[]"),                   /* i[] */
        RPMTAG_CLASSDICT                  (1142, "s[]"),                   /* s[] */
        RPMTAG_FILEDEPENDSX               (1143, "i[]"),                   /* i[] */
        RPMTAG_FILEDEPENDSN               (1144, "i[]"),                   /* i[] */
        RPMTAG_DEPENDSDICT                (1145, "i[]"),                   /* i[] */
        RPMTAG_SOURCESIGMD5               (1146, "x"),                   /* x */
        RPMTAG_FILECONTEXTS               (1147, "s[]"),                   /* s[] - obsolete */
        RPMTAG_FSCONTEXTS                 (1148, "s[]"),                   /* s[] extension */
        RPMTAG_RECONTEXTS                 (1149, "s[]"),                   /* s[] extension */
        RPMTAG_POLICIES                   (1150, "s[]"),                   /* s[] selinux *.te policy file. */
        RPMTAG_PRETRANS                   (1151, "s"),                   /* s */
        RPMTAG_POSTTRANS                  (1152, "s"),                   /* s */
        RPMTAG_PRETRANSPROG               (1153, "s[]"),                   /* s[] */
        RPMTAG_POSTTRANSPROG              (1154, "s[]"),                   /* s[] */
        RPMTAG_DISTTAG                    (1155, "s"),                   /* s */
        RPMTAG_OLDSUGGESTSNAME            (1156, "s[]"),                   /* s[] - obsolete */
        RPMTAG_OLDSUGGESTS                (RPMTAG_OLDSUGGESTSNAME, "s[]"), /* s[] - obsolete */
        RPMTAG_OLDSUGGESTSVERSION         (1157, "s[]"),                   /* s[] - obsolete */
        RPMTAG_OLDSUGGESTSFLAGS           (1158, "i[]"),                   /* i[] - obsolete */
        RPMTAG_OLDENHANCESNAME            (1159, "s[]"),                   /* s[] - obsolete */
        RPMTAG_OLDENHANCES                (RPMTAG_OLDENHANCESNAME, "s[]"), /* s[] - obsolete */
        RPMTAG_OLDENHANCESVERSION         (1160, "s[]"),                   /* s[] - obsolete */
        RPMTAG_OLDENHANCESFLAGS           (1161, "i[]"),                   /* i[] - obsolete */
        RPMTAG_PRIORITY                   (1162, "i[]"),                   /* i[] extension placeholder (unimplemented) */
        RPMTAG_CVSID                      (1163, "s"),                   /* s (unimplemented) */
        RPMTAG_SVNID                      (RPMTAG_CVSID, "s"),           /* s (unimplemented) */
        RPMTAG_BLINKPKGID                 (1164, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_BLINKHDRID                 (1165, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_BLINKNEVRA                 (1166, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_FLINKPKGID                 (1167, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_FLINKHDRID                 (1168, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_FLINKNEVRA                 (1169, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_PACKAGEORIGIN              (1170, "s"),                   /* s (unimplemented) */
        RPMTAG_TRIGGERPREIN               (1171),                   /* internal */
        RPMTAG_BUILDSUGGESTS              (1172),                   /* internal (unimplemented) */
        RPMTAG_BUILDENHANCES              (1173),                   /* internal (unimplemented) */
        RPMTAG_SCRIPTSTATES               (1174, "i[]"),                   /* i[] scriptlet exit codes (unimplemented) */
        RPMTAG_SCRIPTMETRICS              (1175, "i[]"),                   /* i[] scriptlet execution times (unimplemented) */
        RPMTAG_BUILDCPUCLOCK              (1176, "i"),                   /* i (unimplemented) */
        RPMTAG_FILEDIGESTALGOS            (1177, "i[]"),                   /* i[] (unimplemented) */
        RPMTAG_VARIANTS                   (1178, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_XMAJOR                     (1179, "i"),                   /* i (unimplemented) */
        RPMTAG_XMINOR                     (1180, "i"),                   /* i (unimplemented) */
        RPMTAG_REPOTAG                    (1181, "s"),                   /* s (unimplemented) */
        RPMTAG_KEYWORDS                   (1182, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_BUILDPLATFORMS             (1183, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_PACKAGECOLOR               (1184, "i"),                   /* i (unimplemented) */
        RPMTAG_PACKAGEPREFCOLOR           (1185, "i"),                   /* i (unimplemented) */
        RPMTAG_XATTRSDICT                 (1186, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_FILEXATTRSX                (1187, "i[]"),                   /* i[] (unimplemented) */
        RPMTAG_DEPATTRSDICT               (1188, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_CONFLICTATTRSX             (1189, "i[]"),                   /* i[] (unimplemented) */
        RPMTAG_OBSOLETEATTRSX             (1190, "i[]"),                   /* i[] (unimplemented) */
        RPMTAG_PROVIDEATTRSX              (1191, "i[]"),                   /* i[] (unimplemented) */
        RPMTAG_REQUIREATTRSX              (1192, "i[]"),                   /* i[] (unimplemented) */
        RPMTAG_BUILDPROVIDES              (1193),                   /* internal (unimplemented) */
        RPMTAG_BUILDOBSOLETES             (1194),                   /* internal (unimplemented) */
        RPMTAG_DBINSTANCE                 (1195, "i"),                   /* i extension */
        RPMTAG_NVRA                       (1196, "s"),                   /* s extension */
        RPMTAG_FILENAMES                  (5000, "s[]"),                   /* s[] extension */
        RPMTAG_FILEPROVIDE                (5001, "s[]"),                   /* s[] extension */
        RPMTAG_FILEREQUIRE                (5002, "s[]"),                   /* s[] extension */
        RPMTAG_FSNAMES                    (5003, "s[]"),                   /* s[] (unimplemented) */
        RPMTAG_FSSIZES                    (5004, "l[]"),                   /* l[] (unimplemented) */
        RPMTAG_TRIGGERCONDS               (5005, "s[]"),                   /* s[] extension */
        RPMTAG_TRIGGERTYPE                (5006, "s[]"),                   /* s[] extension */
        RPMTAG_ORIGFILENAMES              (5007, "s[]"),                   /* s[] extension */
        RPMTAG_LONGFILESIZES              (5008, "l[]"),                   /* l[] */
        RPMTAG_LONGSIZE                   (5009, "l"),                   /* l */
        RPMTAG_FILECAPS                   (5010, "s[]"),                   /* s[] */
        RPMTAG_FILEDIGESTALGO             (5011, "i"),                   /* i file digest algorithm */
        RPMTAG_BUGURL                     (5012, "s"),                   /* s */
        RPMTAG_EVR                        (5013, "s"),                   /* s extension */
        RPMTAG_NVR                        (5014, "s"),                   /* s extension */
        RPMTAG_NEVR                       (5015, "s"),                   /* s extension */
        RPMTAG_NEVRA                      (5016, "s"),                   /* s extension */
        RPMTAG_HEADERCOLOR                (5017, "i"),                   /* i extension */
        RPMTAG_VERBOSE                    (5018, "i"),                   /* i extension */
        RPMTAG_EPOCHNUM                   (5019, "i"),                   /* i extension */
        RPMTAG_PREINFLAGS                 (5020, "i"),                   /* i */
        RPMTAG_POSTINFLAGS                (5021, "i"),                   /* i */
        RPMTAG_PREUNFLAGS                 (5022, "i"),                   /* i */
        RPMTAG_POSTUNFLAGS                (5023, "i"),                   /* i */
        RPMTAG_PRETRANSFLAGS              (5024, "i"),                   /* i */
        RPMTAG_POSTTRANSFLAGS             (5025, "i"),                   /* i */
        RPMTAG_VERIFYSCRIPTFLAGS          (5026, "i"),                   /* i */
        RPMTAG_TRIGGERSCRIPTFLAGS         (5027, "i[]"),                   /* i[] */
        RPMTAG_COLLECTIONS                (5029, "s[]"),                   /* s[] list of collections (unimplemented) */
        RPMTAG_POLICYNAMES                (5030, "s[]"),                   /* s[] */
        RPMTAG_POLICYTYPES                (5031, "s[]"),                   /* s[] */
        RPMTAG_POLICYTYPESINDEXES         (5032, "i[]"),                   /* i[] */
        RPMTAG_POLICYFLAGS                (5033, "i[]"),                   /* i[] */
        RPMTAG_VCS                        (5034, "s"),                   /* s */
        RPMTAG_ORDERNAME                  (5035, "s[]"),                   /* s[] */
        RPMTAG_ORDERVERSION               (5036, "s[]"),                   /* s[] */
        RPMTAG_ORDERFLAGS                 (5037, "i[]"),                   /* i[] */
        RPMTAG_MSSFMANIFEST               (5038, "s[]"),                   /* s[] reservation (unimplemented) */
        RPMTAG_MSSFDOMAIN                 (5039, "s[]"),                   /* s[] reservation (unimplemented) */
        RPMTAG_INSTFILENAMES              (5040, "s[]"),                   /* s[] extension */
        RPMTAG_REQUIRENEVRS               (5041, "s[]"),                   /* s[] extension */
        RPMTAG_PROVIDENEVRS               (5042, "s[]"),                   /* s[] extension */
        RPMTAG_OBSOLETENEVRS              (5043, "s[]"),                   /* s[] extension */
        RPMTAG_CONFLICTNEVRS              (5044, "s[]"),                   /* s[] extension */
        RPMTAG_FILENLINKS                 (5045, "i[]"),                   /* i[] extension */
        RPMTAG_RECOMMENDNAME              (5046, "s[]"),                   /* s[] */
        RPMTAG_RECOMMENDS                 (RPMTAG_RECOMMENDNAME, "s[]"),   /* s[] */
        RPMTAG_RECOMMENDVERSION           (5047, "s[]"),                   /* s[] */
        RPMTAG_RECOMMENDFLAGS             (5048, "i[]"),                   /* i[] */
        RPMTAG_SUGGESTNAME                (5049, "s[]"),                   /* s[] */
        RPMTAG_SUGGESTS                   (RPMTAG_SUGGESTNAME, "s[]"),     /* s[] */
        RPMTAG_SUGGESTVERSION             (5050, "s[]"),                   /* s[] */
        RPMTAG_SUGGESTFLAGS               (5051, "i[]"),                   /* i[] */
        RPMTAG_SUPPLEMENTNAME             (5052, "s[]"),                   /* s[] */
        RPMTAG_SUPPLEMENTS                (RPMTAG_SUPPLEMENTNAME, "s[]"),  /* s[] */
        RPMTAG_SUPPLEMENTVERSION          (5053, "s[]"),                   /* s[] */
        RPMTAG_SUPPLEMENTFLAGS            (5054, "i[]"),                   /* i[] */
        RPMTAG_ENHANCENAME                (5055, "s[]"),                   /* s[] */
        RPMTAG_ENHANCES                   (RPMTAG_ENHANCENAME, "s[]"),     /* s[] */
        RPMTAG_ENHANCEVERSION             (5056, "s[]"),                   /* s[] */
        RPMTAG_ENHANCEFLAGS               (5057, "i[]"),                   /* i[] */
        RPMTAG_RECOMMENDNEVRS             (5058, "s[]"),                   /* s[] extension */
        RPMTAG_SUGGESTNEVRS               (5059, "s[]"),                   /* s[] extension */
        RPMTAG_SUPPLEMENTNEVRS            (5060, "s[]"),                   /* s[] extension */
        RPMTAG_ENHANCENEVRS               (5061, "s[]"),                   /* s[] extension */
        RPMTAG_ENCODING                   (5062, "s"),                   /* s */
        RPMTAG_FILETRIGGERIN              (5063),                   /* internal */
        RPMTAG_FILETRIGGERUN              (5064),                   /* internal */
        RPMTAG_FILETRIGGERPOSTUN          (5065),                   /* internal */
        RPMTAG_FILETRIGGERSCRIPTS         (5066, "s[]"),                   /* s[] */
        RPMTAG_FILETRIGGERSCRIPTPROG      (5067, "s[]"),                   /* s[] */
        RPMTAG_FILETRIGGERSCRIPTFLAGS     (5068, "i[]"),                   /* i[] */
        RPMTAG_FILETRIGGERNAME            (5069, "s[]"),                   /* s[] */
        RPMTAG_FILETRIGGERINDEX           (5070, "i[]"),                   /* i[] */
        RPMTAG_FILETRIGGERVERSION         (5071, "s[]"),                   /* s[] */
        RPMTAG_FILETRIGGERFLAGS           (5072, "i[]"),                   /* i[] */
        RPMTAG_TRANSFILETRIGGERIN         (5073),                   /* internal */
        RPMTAG_TRANSFILETRIGGERUN         (5074),                   /* internal */
        RPMTAG_TRANSFILETRIGGERPOSTUN     (5075),                   /* internal */
        RPMTAG_TRANSFILETRIGGERSCRIPTS    (5076, "s[]"),                   /* s[] */
        RPMTAG_TRANSFILETRIGGERSCRIPTPROG (5077, "s[]"),                   /* s[] */
        RPMTAG_TRANSFILETRIGGERSCRIPTFLAGS(5078, "i[]"),                   /* i[] */
        RPMTAG_TRANSFILETRIGGERNAME       (5079, "s[]"),                   /* s[] */
        RPMTAG_TRANSFILETRIGGERINDEX      (5080, "i[]"),                   /* i[] */
        RPMTAG_TRANSFILETRIGGERVERSION    (5081, "s[]"),                   /* s[] */
        RPMTAG_TRANSFILETRIGGERFLAGS      (5082, "i[]"),                   /* i[] */
        RPMTAG_REMOVEPATHPOSTFIXES        (5083, "s"),                   /* s internal */
        RPMTAG_FILETRIGGERPRIORITIES      (5084, "i[]"),                   /* i[] */
        RPMTAG_TRANSFILETRIGGERPRIORITIES (5085, "i[]"),                   /* i[] */
        RPMTAG_FILETRIGGERCONDS           (5086, "s[]"),                   /* s[] extension */
        RPMTAG_FILETRIGGERTYPE            (5087, "s[]"),                   /* s[] extension */
        RPMTAG_TRANSFILETRIGGERCONDS      (5088, "s[]"),                   /* s[] extension */
        RPMTAG_TRANSFILETRIGGERTYPE       (5089, "s[]"),                   /* s[] extension */
        RPMTAG_FILESIGNATURES             (5090, "s[]"),                   /* s[] */
        RPMTAG_FILESIGNATURELENGTH        (5091, "i"),                   /* i */
        RPMTAG_PAYLOADSHA256              (5092, "s[]"),                   /* s[] */
        RPMTAG_PAYLOADSHA256ALGO          (5093, "i"),                   /* i (obsolete) */
        RPMTAG_AUTOINSTALLED              (5094, "i"),                   /* i reservation (unimplemented) */
        RPMTAG_IDENTITY                   (5095, "s"),                   /* s reservation (unimplemented) */
        RPMTAG_MODULARITYLABEL            (5096, "s"),                   /* s */
        RPMTAG_PAYLOADSHA256ALT           (5097, "s[]"),                   /* s[] */
        RPMTAG_ARCHSUFFIX                 (5098, "s"),                   /* s extension */
        RPMTAG_SPEC                       (5099, "s"),                   /* s */
        RPMTAG_TRANSLATIONURL             (5100, "s"),                   /* s */
        RPMTAG_UPSTREAMRELEASES           (5101, "s"),                   /* s */
        RPMTAG_SOURCELICENSE              (5102),                   /* internal */
        RPMTAG_PREUNTRANS                 (5103, "s"),                   /* s */
        RPMTAG_POSTUNTRANS                (5104, "s"),                   /* s */
        RPMTAG_PREUNTRANSPROG             (5105, "s[]"),                   /* s[] */
        RPMTAG_POSTUNTRANSPROG            (5106, "s[]"),                   /* s[] */
        RPMTAG_PREUNTRANSFLAGS            (5107, "i"),                   /* i */
        RPMTAG_POSTUNTRANSFLAGS           (5108, "i"),                   /* i */
        RPMTAG_SYSUSERS                   (5109, "s[]"),                   /* s[] extension */
        RPMTAG_BUILDSYSTEM                (5110),                   /* internal */
        RPMTAG_BUILDOPTION                (5111),                   /* internal */
        RPMTAG_PAYLOADSIZE                (5112, "l"),                   /* l */
        RPMTAG_PAYLOADSIZEALT             (5113, "l"),                   /* l */
        RPMTAG_RPMFORMAT                  (5114, "i"),                   /* i */
        RPMTAG_FILEMIMEINDEX              (5115, "i[]"),                   /* i[] */
        RPMTAG_MIMEDICT                   (5116, "s[]"),                   /* s[] */
        RPMTAG_FILEMIMES                  (5117, "s[]"),                   /* s[] extension */
        RPMTAG_PACKAGEDIGESTS             (5118, "s[]"),                   /* s[] */
        RPMTAG_PACKAGEDIGESTALGOS         (5119, "i[]"),                   /* i[] */
        RPMTAG_SOURCENEVR                 (5120, "s"),                   /* s */
        RPMTAG_PAYLOADSHA512              (5121, "s"),                   /* s */
        RPMTAG_PAYLOADSHA512ALT           (5122, "s"),                   /* s */
        RPMTAG_PAYLOADSHA3_256            (5123, "s"),                   /* s */
        RPMTAG_PAYLOADSHA3_256ALT         (5124, "s"),                   /* s */
        RPMTAG_FIRSTFREE_TAG              (5125),                   /*!< internal */
        // @formatter:on
        ;

        private final int tagValue;
        private final String dataType;

        RpmTag(RpmTag other, String dataType) {
            this(other.tagValue, dataType);
        }

        RpmTag(int tagValue) {
            this(tagValue, null);
        }
    }

    @Getter
    enum RpmSigTag implements RpmTags {
        // @formatter:off
        RPMSIGTAG_SIZE      (1000), /*!< internal Header+Payload size (32bit) in bytes. */
        RPMSIGTAG_LEMD5_1   (1001), /*!< internal Broken MD5, take 1 @deprecated legacy. */
        RPMSIGTAG_PGP       (1002), /*!< internal PGP 2.6.3 signature. */
        RPMSIGTAG_LEMD5_2   (1003), /*!< internal Broken MD5, take 2 @deprecated legacy. */
        RPMSIGTAG_MD5       (1004), /*!< internal MD5 signature. */
        RPMSIGTAG_GPG       (1005), /*!< internal GnuPG signature. */
        RPMSIGTAG_PGP5      (1006), /*!< internal PGP5 signature @deprecated legacy. */
        RPMSIGTAG_PAYLOADSIZE (1007),/*!< internal uncompressed payload size (32bit) in bytes. */
        RPMSIGTAG_RESERVEDSPACE (1008),/*!< internal space reserved for signatures */
        RPMSIGTAG_BADSHA1_1 (RpmTag.RPMTAG_BADSHA1_1),     /*!< internal Broken SHA1, take 1. */
        RPMSIGTAG_BADSHA1_2 (RpmTag.RPMTAG_BADSHA1_2),     /*!< internal Broken SHA1, take 2. */
        RPMSIGTAG_DSA       (RpmTag.RPMTAG_DSAHEADER),     /*!< internal DSA header signature. */
        RPMSIGTAG_RSA       (RpmTag.RPMTAG_RSAHEADER),     /*!< internal RSA header signature. */
        RPMSIGTAG_SHA1      (RpmTag.RPMTAG_SHA1HEADER),    /*!< internal sha1 header digest. */
        RPMSIGTAG_LONGSIZE  (RpmTag.RPMTAG_LONGSIGSIZE),   /*!< internal Header+Payload size (64bit) in bytes. */
        RPMSIGTAG_LONGARCHIVESIZE (RpmTag.RPMTAG_LONGARCHIVESIZE), /*!< internal uncompressed payload size (64bit) in bytes. */
        RPMSIGTAG_SHA256    (RpmTag.RPMTAG_SHA256HEADER),
        RPMSIGTAG_FILESIGNATURES            (RpmTag.RPMTAG_SIG_BASE.tagValue + 18),
        RPMSIGTAG_FILESIGNATURELENGTH       (RpmTag.RPMTAG_SIG_BASE.tagValue + 19),
        RPMSIGTAG_VERITYSIGNATURES          (RpmTag.RPMTAG_VERITYSIGNATURES),
        RPMSIGTAG_VERITYSIGNATUREALGO       (RpmTag.RPMTAG_VERITYSIGNATUREALGO),
        RPMSIGTAG_OPENPGP                   (RpmTag.RPMTAG_OPENPGP),
        RPMSIGTAG_SHA3_256                  (RpmTag.RPMTAG_SHA3_256HEADER),
        RPMSIGTAG_RESERVED                  (RpmTag.RPMTAG_SIG_TOP),
        // @formatter:on
        ;

        private final int tagValue;
        private final RpmTag rpmTag;

        RpmSigTag(int tagValue) {
            this.tagValue = tagValue;
            this.rpmTag = null;
        }

        RpmSigTag(RpmTag rpmTag) {
            this.tagValue = rpmTag.tagValue;
            this.rpmTag = rpmTag;
        }
    }


    /**
     * @see <a href=https://rpm-software-management.github.io/rpm/manual/format_v4.html>rpm-software-management.github.io: format_v4.html</a>
     */
    @Getter
    @RequiredArgsConstructor
    enum SignatureTag implements RpmTags {
        HEADERSIGNATURES(RpmTag.RPMTAG_HEADERSIGNATURES),
        DSA(RpmTag.RPMTAG_DSAHEADER),
        RSA(RpmTag.RPMTAG_RSAHEADER),
        SHA1(RpmTag.RPMTAG_SHA1HEADER),
        LONGSIZE(RpmTag.RPMTAG_LONGSIZE),
        LONGARCHIVESIZE(RpmTag.RPMTAG_LONGARCHIVESIZE),
        SHA256(RpmTag.RPMTAG_SHA256HEADER),
        FILESIGNATURES(RpmSigTag.RPMSIGTAG_FILESIGNATURES),
        FILESIGNATURELENGTH(RpmSigTag.RPMSIGTAG_FILESIGNATURELENGTH),
        VERITYSIGNATURES(RpmTag.RPMTAG_VERITYSIGNATURES),
        VERITYSIGNATUREALGO(RpmTag.RPMTAG_VERITYSIGNATUREALGO),
        OPENPGP(RpmTag.RPMTAG_OPENPGP),
        SHA3_256(RpmTag.RPMTAG_SHA3_256HEADER),
        SIZE(RpmSigTag.RPMSIGTAG_SIZE),
        PGP(RpmSigTag.RPMSIGTAG_PGP),
        MD5(RpmSigTag.RPMSIGTAG_MD5),
        GPG(RpmSigTag.RPMSIGTAG_GPG),
        RESERVEDSPACE(RpmSigTag.RPMSIGTAG_RESERVEDSPACE),

        // unofficial, found in wild
        PAYLOADSIZE(RpmSigTag.RPMSIGTAG_PAYLOADSIZE),
        ;

        private final RpmTags tag;

        @Override
        public int getTagValue() {
            return tag.getTagValue();
        }
    }


    @Getter
    @RequiredArgsConstructor
    enum RpmDbiTag implements RpmTags {
        // @formatter:off
        RPMDBI_PACKAGES            (0),                                     /* Installed package headers. */
        RPMDBI_LABEL               (2),                                     /* NEVRA label pseudo index */
        RPMDBI_NAME                (RpmTag.RPMTAG_NAME.tagValue),
        RPMDBI_BASENAMES           (RpmTag.RPMTAG_BASENAMES.tagValue),
        RPMDBI_GROUP               (RpmTag.RPMTAG_GROUP.tagValue),
        RPMDBI_REQUIRENAME         (RpmTag.RPMTAG_REQUIRENAME.tagValue),
        RPMDBI_PROVIDENAME         (RpmTag.RPMTAG_PROVIDENAME.tagValue),
        RPMDBI_CONFLICTNAME        (RpmTag.RPMTAG_CONFLICTNAME.tagValue),
        RPMDBI_OBSOLETENAME        (RpmTag.RPMTAG_OBSOLETENAME.tagValue),
        RPMDBI_TRIGGERNAME         (RpmTag.RPMTAG_TRIGGERNAME.tagValue),
        RPMDBI_DIRNAMES            (RpmTag.RPMTAG_DIRNAMES.tagValue),
        RPMDBI_INSTALLTID          (RpmTag.RPMTAG_INSTALLTID.tagValue),
        RPMDBI_SIGMD5              (RpmTag.RPMTAG_SIGMD5.tagValue),      /* OBSOLETE */
        RPMDBI_SHA1HEADER          (RpmTag.RPMTAG_SHA1HEADER.tagValue),  /* OBSOLETE */
        RPMDBI_INSTFILENAMES       (RpmTag.RPMTAG_INSTFILENAMES.tagValue),
        RPMDBI_FILETRIGGERNAME     (RpmTag.RPMTAG_FILETRIGGERNAME.tagValue),
        RPMDBI_TRANSFILETRIGGERNAME(RpmTag.RPMTAG_TRANSFILETRIGGERNAME.tagValue),
        RPMDBI_RECOMMENDNAME       (RpmTag.RPMTAG_RECOMMENDNAME.tagValue),
        RPMDBI_SUGGESTNAME         (RpmTag.RPMTAG_SUGGESTNAME.tagValue),
        RPMDBI_SUPPLEMENTNAME      (RpmTag.RPMTAG_SUPPLEMENTNAME.tagValue),
        RPMDBI_ENHANCENAME         (RpmTag.RPMTAG_ENHANCENAME.tagValue),
        // @formatter:on
        ;
        private final int tagValue;
    }

    @Getter
    @RequiredArgsConstructor
    enum RpmTagType {
        RPM_NULL_TYPE(0),
        RPM_CHAR_TYPE(1),
        RPM_INT8_TYPE(2),
        RPM_INT16_TYPE(3),
        RPM_INT32_TYPE(4),
        RPM_INT64_TYPE(5),
        RPM_STRING_TYPE(6),
        RPM_BIN_TYPE(7),
        RPM_STRING_ARRAY_TYPE(8),
        RPM_I18NSTRING_TYPE(9),
        ;

        private static final Map<Integer, RpmTagType> BY_VALUE = Arrays.stream(values())
                .collect(Collectors.toMap(RpmTagType::getValue, Function.identity()));

        private final int value;

        public static RpmTagType ofValue(int i) {
            return Objects.requireNonNull(BY_VALUE.get(i));
        }

        public interface Constants {
            int RPM_MIN_TYPE = 1;
            int RPM_MAX_TYPE = 9;
            int RPM_FORCEFREE_TYPE = 0xff;
            int RPM_MASK_TYPE = 0x0000ffff;
        }
    }

    @Getter
    @RequiredArgsConstructor
    enum RpmTagClass {
        RPM_NULL_CLASS(0),
        RPM_NUMERIC_CLASS(1),
        RPM_STRING_CLASS(2),
        RPM_BINARY_CLASS(3),
        ;

        private final int value;
    }

    @Getter
    @RequiredArgsConstructor
    enum RpmTagReturnType {
        RPM_ANY_RETURN_TYPE(0),
        RPM_SCALAR_RETURN_TYPE(0x00010000),
        RPM_ARRAY_RETURN_TYPE(0x00020000),
        RPM_MAPPING_RETURN_TYPE(0x00040000),
        ;
        private final int value;

        public interface Constants {
            int RPM_MASK_RETURN_TYPE = 0xffff0000;
        }

    }
}
